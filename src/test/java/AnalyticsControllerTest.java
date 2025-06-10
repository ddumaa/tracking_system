import com.project.tracking_system.controller.AnalyticsController;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.analytics.PostalServiceStatisticsService;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import com.project.tracking_system.service.analytics.StoreDashboardDataService;
import com.project.tracking_system.service.analytics.AnalyticsResetService;
import com.project.tracking_system.service.store.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostalServiceStatisticsService postalStatisticsService;
    @MockBean
    private StoreAnalyticsService storeAnalyticsService;
    @MockBean
    private StoreService storeService;
    @MockBean
    private StoreDashboardDataService storeDashboardDataService;
    @MockBean
    private WebSocketController webSocketController;
    @MockBean
    private AnalyticsResetService analyticsResetService;

    private Authentication auth(User user) {
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    @Test
    void getAnalyticsJson_Unauthorized_Returns401() throws Exception {
        mockMvc.perform(get("/analytics/json").with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAnalytics_Unauthorized_Returns401() throws Exception {
        mockMvc.perform(post("/analytics/update").with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAnalyticsJson_ReturnsPiePeriodAndStoreStats() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setTimeZone("UTC");
        user.setRole(Role.ROLE_USER);

        Store store = new Store();
        store.setId(2L);
        store.setName("Store");

        StoreStatistics stat = new StoreStatistics();
        stat.setStore(store);
        stat.setTotalSent(10);
        stat.setTotalDelivered(5);
        stat.setTotalReturned(1);

        when(storeService.getUserStores(user.getId())).thenReturn(List.of(store));
        when(storeService.getStore(store.getId(), user.getId())).thenReturn(store);
        when(storeAnalyticsService.getStoreStatistics(store.getId())).thenReturn(Optional.of(stat));
        when(storeDashboardDataService.calculatePieData(List.of(stat)))
                .thenReturn(Map.of("delivered",5,"returned",1,"inTransit",4));
        when(storeDashboardDataService.getFullPeriodStatsChart(
                eq(List.of(store.getId())), eq(ChronoUnit.WEEKS), any()))
                .thenReturn(Map.of(
                        "labels", List.of("w1"),
                        "sent", List.of(1),
                        "delivered", List.of(1),
                        "returned", List.of(0)
                ));

        mockMvc.perform(get("/analytics/json")
                        .param("storeId", "2")
                        .param("interval", "weeks")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieData.delivered").value(5))
                .andExpect(jsonPath("$.pieData.returned").value(1))
                .andExpect(jsonPath("$.pieData.inTransit").value(4))
                .andExpect(jsonPath("$.periodStats.labels[0]").value("w1"))
                .andExpect(jsonPath("$.storeStatistics.totalSent").value(10))
                .andExpect(jsonPath("$.storeStatistics.totalDelivered").value(5))
                .andExpect(jsonPath("$.storeStatistics.totalReturned").value(1))
                .andExpect(jsonPath("$.storeStatistics.averageDeliveryDays").value(0))
                .andExpect(jsonPath("$.storeStatistics.averagePickupDays").value(0));
    }

    @Test
    void getAnalyticsJson_StoreAccessDenied_Returns403() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setTimeZone("UTC");
        user.setRole(Role.ROLE_USER);

        // storeService.getStore будет выбрасывать SecurityException
        when(storeService.getUserStores(user.getId())).thenReturn(List.of());
        when(storeService.getStore(2L, user.getId())).thenThrow(new SecurityException());

        mockMvc.perform(get("/analytics/json")
                        .param("storeId", "2")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAnalytics_CallsService() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setTimeZone("UTC");
        user.setRole(Role.ROLE_USER);

        Store store = new Store();
        store.setId(2L);
        store.setName("Store");

        when(storeService.getStore(store.getId(), user.getId())).thenReturn(store);

        mockMvc.perform(post("/analytics/update")
                        .param("storeId", "2")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(user))))
                .andExpect(status().isOk());

        verify(storeAnalyticsService).updateStoreAnalytics(2L);
    }

    @Test
    void resetAllAnalytics_CallsService() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.ROLE_USER);

        mockMvc.perform(post("/analytics/reset/all")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(user))))
                .andExpect(status().isOk());

        verify(analyticsResetService).resetAllAnalytics(1L);
    }

    @Test
    void resetAnalyticsForStore_CallsService() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.ROLE_USER);

        mockMvc.perform(post("/analytics/reset/store/5")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(user))))
                .andExpect(status().isOk());

        verify(analyticsResetService).resetStoreAnalytics(1L, 5L);
    }
}
