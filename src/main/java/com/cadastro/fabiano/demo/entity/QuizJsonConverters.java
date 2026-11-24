package com.cadastro.fabiano.demo.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cadastro.fabiano.demo.dto.QuizRegisterField;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Guarda listas/mapas pequenos do quiz como JSON em coluna TEXT. */
public final class QuizJsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuizJsonConverters() {}

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao gravar JSON do quiz", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler JSON do quiz", e);
        }
    }

    @Converter
    public static class Fields implements AttributeConverter<List<QuizRegisterField>, String> {
        @Override
        public String convertToDatabaseColumn(List<QuizRegisterField> v) {
            return v == null || v.isEmpty() ? null : write(v);
        }

        @Override
        public List<QuizRegisterField> convertToEntityAttribute(String db) {
            return db == null ? null : read(db, new TypeReference<>() {});
        }
    }

    @Converter
    public static class Data implements AttributeConverter<Map<String, String>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, String> v) {
            return v == null || v.isEmpty() ? null : write(v);
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String db) {
            return db == null ? null : read(db, new TypeReference<LinkedHashMap<String, String>>() {});
        }
    }
}
