package com.project.tracking_system.controller;

import com.project.tracking_system.dto.EvroTrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.service.JsonService.JsonEvroTrackingService;
import com.project.tracking_system.service.StatusIconService;
import com.project.tracking_system.service.TrackParcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/history")
public class HistoryController {

    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final TrackParcelService trackParcelService;
    private final StatusIconService statusIconService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;

    @Autowired
    public HistoryController(JsonEvroTrackingService jsonEvroTrackingService, TrackParcelService trackParcelService,
                             StatusIconService statusIconService,
                             JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper) {
        this.jsonEvroTrackingService = jsonEvroTrackingService;
        this.trackParcelService = trackParcelService;
        this.statusIconService = statusIconService;
        this.jsonEvroTrackingResponseMapper = jsonEvroTrackingResponseMapper;

    }

    @GetMapping()
    public String history(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        trackParcelService.updateHistory(auth.getName());
        List<TrackParcelDTO> byUserTrack = trackParcelService.findByUserTracks(auth.getName());
        if (byUserTrack.isEmpty()) {
            model.addAttribute("trackParcelNotification", "Отслеживаемых посылок нет");
        } else {
            model.addAttribute("trackParcelDTO", byUserTrack);
            model.addAttribute("statusIconService", statusIconService);
        }
        return "history";
    }

    @GetMapping("/{itemNumber}")
    public String history(Model model, @PathVariable("itemNumber") String itemNumber) {
        EvroTrackInfoListDTO evroTrackInfoListDTO = jsonEvroTrackingResponseMapper.
                mapJsonEvroTrackingResponseToDTO(jsonEvroTrackingService.getJson(itemNumber));
        model.addAttribute("jsonEvroTracking", evroTrackInfoListDTO);
        model.addAttribute("itemNumber", itemNumber);
        return "partials/history-info";
    }

    @PostMapping("/history-update")
    public String history(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        trackParcelService.updateHistory(auth.getName());
        return "redirect:/history";
    }

}
