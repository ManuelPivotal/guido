package org.guido.persistence.domain.repository;

import org.guido.persistence.domain.MethodPerformanceCapture;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.MongoRepository;

//@Repository
@Configuration
public interface MethodPerformanceCaptureRepository extends MongoRepository<MethodPerformanceCapture, String> {

}
