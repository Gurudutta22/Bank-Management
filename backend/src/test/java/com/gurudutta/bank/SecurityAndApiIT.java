package com.gurudutta.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks over the real HTTP layer: filters, security rules, validation and the
 * global error contract all participate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.seed.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:security-it;DB_CLOSE_DELAY=-1",
})
@DisplayName("HTTP API and security")
class SecurityAndApiIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    /* ------------------------------------------------------------ authentication */

    @Test
    @DisplayName("a protected endpoint without a token returns 401 as JSON, not an HTML redirect")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("a wrong password returns a message that does not reveal whether the email exists")
    void wrongPasswordIsVague() throws Exception {
        MvcResult knownEmail = login("priya@novabank.io", "TotallyWrong@1");
        MvcResult unknownEmail = login("nobody@novabank.io", "TotallyWrong@1");

        assertThat(knownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        // Identical wording either way: the response must not be an account-existence oracle.
        assertThat(body(knownEmail).get("message").asText())
                .isEqualTo(body(unknownEmail).get("message").asText());
    }

    @Test
    @DisplayName("a garbage bearer token is ignored rather than throwing")
    void malformedTokenIsRejectedCleanly() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void refreshTokenIsNotAcceptedAsAccessToken() throws Exception {
        JsonNode tokens = body(login("priya@novabank.io", "Customer@123"));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + tokens.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    /* ------------------------------------------------------------- authorization */

    @Test
    @DisplayName("a customer is refused at the admin endpoints")
    void customerCannotReachAdminEndpoints() throws Exception {
        String token = accessToken("priya@novabank.io", "Customer@123");

        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an admin can reach the admin endpoints")
    void adminCanReachAdminEndpoints() throws Exception {
        String token = accessToken("admin@novabank.io", "Admin@123");

        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.totalHoldings").isNumber());
    }

    @Test
    @DisplayName("one customer cannot read another customer's account")
    void customerCannotReadAnotherCustomersAccount() throws Exception {
        String rahul = accessToken("rahul@novabank.io", "Customer@123");

        // Priya's seeded account. 404 rather than 403, so the response does not confirm it exists.
        mockMvc.perform(get("/api/v1/accounts/900100100101")
                        .header("Authorization", "Bearer " + rahul))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("one customer cannot debit another customer's account")
    void customerCannotDebitAnotherCustomersAccount() throws Exception {
        String rahul = accessToken("rahul@novabank.io", "Customer@123");

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", "Bearer " + rahul)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountNumber":"900100100101",
                                 "toAccountNumber":"900100100201",
                                 "amount":1000}"""))
                .andExpect(status().isNotFound());
    }

    /* ---------------------------------------------------------------- validation */

    @Test
    @DisplayName("registration rejects a weak password with per-field detail")
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Weak Password","email":"weak-it@test.io",
                                 "phone":"9812345678","password":"alllowercase"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    @DisplayName("registration rejects a duplicate email")
    void duplicateEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Copy Cat","email":"priya@novabank.io",
                                 "phone":"9899999999","password":"Strong@123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("self-registration cannot mint an administrator")
    void registrationAlwaysCreatesACustomer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        // "role" is not part of the DTO, so this is silently ignored - which is
                        // exactly the point: the field cannot be reached from outside.
                        .content("""
                                {"fullName":"Would Be Admin","email":"escalate-it@test.io",
                                 "phone":"9788888888","password":"Strong@123","role":"ADMIN"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(body(result).get("user").get("role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("a negative deposit is rejected by bean validation")
    void negativeAmountIsRejected() throws Exception {
        String token = accessToken("priya@novabank.io", "Customer@123");

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"900100100101","amount":-500}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /* -------------------------------------------------------------- happy paths */

    @Test
    @DisplayName("a customer can open an account, deposit into it and see the new balance")
    void openDepositAndRead() throws Exception {
        String token = accessToken("ananya@novabank.io", "Customer@123");

        MvcResult opened = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SAVINGS","openingBalance":2000}"""))
                .andExpect(status().isCreated())
                .andReturn();

        String accountNumber = body(opened).get("accountNumber").asText();

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"%s","amount":750.50}""".formatted(accountNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(2750.50));

        mockMvc.perform(get("/api/v1/accounts/" + accountNumber)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(2750.50));
    }

    @Test
    @DisplayName("the statement downloads as CSV with an attachment header")
    void statementDownloadsAsCsv() throws Exception {
        String token = accessToken("priya@novabank.io", "Customer@123");

        MvcResult result = mockMvc.perform(get("/api/v1/accounts/900100100101/statement")
                        .param("from", "2020-01-01")
                        .param("to", "2030-12-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).startsWith("Date,Reference,Type,Description,Category,Debit,Credit,Balance");
        assertThat(csv).contains("Closing balance");
    }

    @Test
    @DisplayName("an unknown endpoint returns the standard JSON error shape")
    void unknownEndpointReturnsJson() throws Exception {
        String token = accessToken("priya@novabank.io", "Customer@123");

        mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /* -------------------------------------------------------------------- utils */

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn();
    }

    private String accessToken(String email, String password) throws Exception {
        return body(login(email, password)).get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }
}
