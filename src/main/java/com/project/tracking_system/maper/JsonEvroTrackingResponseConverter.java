package com.project.tracking_system.maper;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonEvroTrackingResponseConverter {
    private ModelMapper modelMapper = new ModelMapper();
}
