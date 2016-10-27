package org.guido.server.service;

import java.util.List;

import org.guido.persistence.domain.MethodPerformanceCapture;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MethodPerformanceCaptureService {
	
//	@Autowired
//	MethodPerformanceCaptureRepository captureRepository;
	
	public void insertNewCaptureRecords(List<MethodPerformanceCapture> records) {
//		captureRepository.save(records);
	}

	public void insertNewCaptureRecord(MethodPerformanceCapture record) {
//		captureRepository.save(record);
	}
}
