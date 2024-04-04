package app.controllers;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.entities.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.config.ApplicationConfig;
import app.config.HibernateConfig;
import app.dtos.EventDTO;
import app.dtos.TokenDTO;
import app.entities.Event;
import app.entities.Status;
import app.entities.User;
import app.utils.Routes;
import app.utils.TestUtils;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;

public class EventControllerTests {
    private static ApplicationConfig appConfig;
    private static final String BASE_URL = "http://localhost:7777/api";
    private static EntityManagerFactory emfTest;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void beforeAll() {
        RestAssured.baseURI = BASE_URL;
        objectMapper.findAndRegisterModules();

        // Setup test database using docker testcontainers
        emfTest = HibernateConfig.getEntityManagerFactory(true);

        Routes routes = Routes.getInstance(emfTest);
        // Start server
        appConfig = ApplicationConfig.getInstance(emfTest);
        appConfig
                .initiateServer()
                .setExceptionHandling()
                .checkSecurityRoles()
                .setRoute(routes.eventResources())
                .setRoute(routes.testResources())
                .setRoute(routes.securityResources())
                .setRoute(routes.securedRoutes())
                .setRoute(routes.unsecuredRoutes())
                .startServer(7777)
        ;
    }

    @BeforeEach
    public void setUpEach() {
        // Setup test database for each test
        TestUtils.createUsersAndRoles(emfTest);
        TestUtils.createEvents(emfTest);
        TestUtils.addEventToUser(emfTest);
    }

    @AfterAll
    static void afterAll() {
        emfTest.close();
        appConfig.stopServer();
    }


    @Test
    public void getEventById() throws JsonMappingException, JsonProcessingException {
        Event first = TestUtils.getEvents(emfTest).values().stream().findFirst().get();
        Response response = given().when()
                .get("/events/" + first.getId()).peek();

        EventDTO actualEvent = objectMapper.readValue(response.body().asString(), EventDTO.class);

        assertEquals(first.getTitle(), actualEvent.getTitle());
    }


    @Test
    void registerUserToEvent() {
        String requestLoginBody = "{\"email\": \"user\",\"password\": \"user\"}";
        TokenDTO token = given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());

        String requestBody = "{\"email\": \"user\",\"id\": \"1\"}";
        RestAssured.given()
                .contentType("application/json")
                .header(header)
                .body(requestBody)
                .when()
                .put("/event/registerUser")
                .then()
                .statusCode(200);

        try (EntityManager em = emfTest.createEntityManager()) {
            Event event = em.createQuery("FROM Event e WHERE e.id = 1", Event.class).getSingleResult();
            assertEquals(1, event.getUsers().size());
        }
    }

    @Test
    void cancelRegistration() {
        String requestLoginBody = "{\"email\": \"user\",\"password\": \"user\"}";
        TokenDTO token = RestAssured
                .given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());

        String requestBody = "{\"email\": \"user\",\"id\": \"2\"}";
        RestAssured.given()
                .contentType("application/json")
                .header(header)
                .body(requestBody)
                .when()
                .put("/event/cancelRegistration")
                .then()
                .statusCode(200);
    }

    @Test
    void getUpcomingEvents() {


        String dateAsString =
                given()
                        .when()
                        .get("event/upcoming")
                        .then()
                        .statusCode(200)
                        .body("[0].dateOfEvent", notNullValue())
                        //.body("[0].dateOfEvent", matchesPattern("yyyy-MM-dd"))
                        .extract()
                        .path("[0].dateOfEvent");

        LocalDate date = LocalDate.parse(dateAsString);

        assertThat(date, greaterThan(LocalDate.now()));

    }

    @Test
    void getEventByCategory(){
        Map<String,Event> events = TestUtils.getEvents(emfTest);
        var eventsByCategory = given().when()
            .get("/events/category/category")
            .then()
            .statusCode(200)
            .extract()
            .as(EventDTO[].class)
            ;
        assertEquals(events.size(), eventsByCategory.length);
        
    }

    @Test
    void getEventByStatus(){
        Map<String,Event> activeEvents = TestUtils.getEvents(emfTest).values().stream().filter(e -> e.getStatus().equals(Status.ACTIVE)).collect(Collectors.toMap(e -> e.getTitle(), e -> e));
        var eventsByCategory = given().when()
            .get("/events/status/active")
            .then()
            .statusCode(200)
            .extract()
            .as(EventDTO[].class)
            ;
        assertEquals(activeEvents.size(), eventsByCategory.length);
        
    }

    @Test
    void updateEvent() {

        String requestLoginBody = "{\"email\": \"instructor\",\"password\": \"instructor\"}";

        TokenDTO token = given()
                .contentType("application/json")
                .body(requestLoginBody)
                .when()
                .post("/auth/login")
                .then()
                .extract()
                .as(TokenDTO.class);

        Header header = new Header("Authorization", "Bearer " + token.getToken());

        String updateBody = "{\"title\": \"title4\",\"startTime\": \"18:00 pm\", \"description\": \"Football practice\"" +
                ",\"dateOfEvent\": \"2024-04-04\",\"durationInHours\": "+ 2 +",\"maxNumberOfStudents\": " + 44 +
                ", \"locationOfEvent\": \"KFUM Boldkblub Kbh\",\"instructor\": \"instructor\",\"price\": " + 20d +
                ",\"category\": \"Sport\",\"image\": \"image\",\"status\": \"UPCOMING\"}";

        given()
                .when()
                .header(header)
                .body(updateBody)
                /*.body(new Event("title4", "startTime", "description",
                        LocalDate.of(2024, 04, 29),
                        100, 44,
                        "locationOfEvent", "instructor", 20d, "Sport",
                        "image", Status.UPCOMING))

                 */
                .when()
                .request("PUT", "events/4")
                .then()
                .statusCode(201);

        given()
                .when()
                .get("events/4")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .assertThat()
                .body("id", is(4))
                .assertThat()
                .body("category", equalTo("Sport"))
                .body("maxNumberOfStudents", is(44))
                .body("title", equalTo("title4"));

    }
}




