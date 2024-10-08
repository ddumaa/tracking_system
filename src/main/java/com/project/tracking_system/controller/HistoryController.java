package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.service.StatusTrackService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.TrackParcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/history")
public class HistoryController {

    private final TrackParcelService trackParcelService;
    private final StatusTrackService statusTrackService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @Autowired
    public HistoryController(TrackParcelService trackParcelService, StatusTrackService statusTrackService,
                             TypeDefinitionTrackPostService typeDefinitionTrackPostService) {
        this.trackParcelService = trackParcelService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.statusTrackService = statusTrackService;
    }

    @GetMapping()
    public String history(
            @RequestParam(value = "status", required = false) String statusString,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Page<TrackParcelDTO> trackParcelPage;
        GlobalStatus status = null;
        if (statusString != null && !statusString.isEmpty()) {
            try {
                status = GlobalStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                model.addAttribute("trackParcelNotification", "Неверный статус посылки");
                return "history";
            }
        }
        if (status != null) {
            trackParcelPage = trackParcelService.findByUserTracksAndStatus(auth.getName(), status, page, size);
        } else {
            trackParcelPage = trackParcelService.findByUserTracks(auth.getName(), page, size);
        }
        model.addAttribute("size", size);
        model.addAttribute("trackParcelDTO", trackParcelPage.getContent());
        model.addAttribute("statusString", statusString);
        model.addAttribute("currentPage", trackParcelPage.getNumber());
        model.addAttribute("totalPages", trackParcelPage.getTotalPages());
        model.addAttribute("trackParcelNotification", trackParcelPage.isEmpty() ? "Отслеживаемых посылок нет" : null);
        model.addAttribute("statusTrackService", statusTrackService);
        return "history";
    }

    @GetMapping("/{itemNumber}")
    public String history(Model model, @PathVariable("itemNumber") String itemNumber) {
        TrackInfoListDTO trackInfoListDTO = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(itemNumber);
        model.addAttribute("jsonTracking", trackInfoListDTO);
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