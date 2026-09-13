package com.pickuppass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.dto.InquiryChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiInquiryServiceTest {

    private AiInquiryService disabledService() {
        return new AiInquiryService(
                new ObjectMapper(),
                false,
                "",
                "gpt-5.6-luna",
                "https://api.openai.com/v1/responses",
                420,
                15,
                12,
                300);
    }

    @Test
    void rejectsBlankQuestionsBeforeCallingProvider() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> disabledService().answer(
                        new InquiryChatRequest("   ", List.of()),
                        new MockHttpServletRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void failsClosedWhenAssistantIsNotConfigured() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> disabledService().answer(
                        new InquiryChatRequest(
                                "How does guardian verification work?",
                                List.of()),
                        new MockHttpServletRequest()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void rejectsOversizedPublicQuestions() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> disabledService().answer(
                        new InquiryChatRequest("x".repeat(801), List.of()),
                        new MockHttpServletRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
