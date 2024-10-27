package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrackingNumberServiceXLS {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;

    @Autowired
    public TrackingNumberServiceXLS(TypeDefinitionTrackPostService typeDefinitionTrackPostService, TrackParcelService trackParcelService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.trackParcelService = trackParcelService;
    }

    public List<TrackingResultAdd> processTrackingNumber(MultipartFile file, String authenticatedUser) throws IOException {
        List<TrackingResultAdd> trackingResult = new ArrayList<>();

        try(InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (Row row : sheet) {
                Cell cell = row.getCell(0);
                if (cell != null) {
                    String trackingNumber  = cell.getStringCellValue();
                    TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(trackingNumber);

                    String status;
                    if (trackInfo != null && !trackInfo.getList().isEmpty()) {
                        trackParcelService.save(trackingNumber, trackInfo, authenticatedUser);
                        status = "Добавлен";
                    } else {
                        status = "Ошибка: некорректные данные";
                    }
                    trackingResult.add(new TrackingResultAdd(trackingNumber, status));
                }
            }
        }
        return trackingResult;
    }

}