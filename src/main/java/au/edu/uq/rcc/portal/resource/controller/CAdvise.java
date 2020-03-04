package au.edu.uq.rcc.portal.resource.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class CAdvise extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(CAdvise.class);

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {

		if(HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
			status = HttpStatus.SERVICE_UNAVAILABLE;
		}

		LOGGER.error("Caught exception", e);

		return ResponseEntity.status(status).headers(headers).build();
	}
}
