package it.gov.pagopa.bizeventsdatastore.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtil {

	public static <T> T readModelFromFile(String relativePath, Class<T> clazz) throws IOException {
		ClassLoader classLoader = TestUtil.class.getClassLoader();
		File file = new File(Objects.requireNonNull(classLoader.getResource(relativePath)).getPath());
		var content = Files.readString(file.toPath());
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		return objectMapper.readValue(content, clazz);
	}

	/**
	 * @param object to map into the Json string
	 * @return object as Json string
	 * @throws JsonProcessingException if there is an error during the parsing of
	 *                                 the object
	 */
	public String toJson(Object object) throws JsonProcessingException {
		return new ObjectMapper().writeValueAsString(object);
	}

}
