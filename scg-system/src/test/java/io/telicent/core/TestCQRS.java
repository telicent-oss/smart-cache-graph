package io.telicent.core;

import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.kafka.KafkaTestCluster;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.server.CounterSet;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.servlets.ActionService;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.shared.OperationDeniedException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateException;
import org.apache.jena.web.HttpSC;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@SuppressWarnings({ "unchecked" })
public class TestCQRS {

    @Test
    public void givenNoProducerProperties_whenCreatingAction_thenCreatedWithoutProducer() {
        // Given and When
        ActionService action = CQRS.updateAction(KafkaTestCluster.DEFAULT_TOPIC, null);

        // Then
        Assertions.assertInstanceOf(SPARQL_Update_CQRS.class, action);
        SPARQL_Update_CQRS cqrs = (SPARQL_Update_CQRS) action;
        Assertions.assertNull(cqrs.getProducer());
    }

    @Test
    public void givenNullTxnConsumers_whenStartingOperation_thenUpdateCtlCallsDoNothing() {
        // Given
        HttpAction action = mock(HttpAction.class);
        Context context = new Context();
        when(action.getContext()).thenReturn(context);
        DatasetGraph dsg = mock(DatasetGraph.class);

        // When
        CQRS.UpdateCQRS update =
                CQRS.startOperation(KafkaTestCluster.DEFAULT_TOPIC, null, action, dsg, null, null, null);
        update.changes().txnCommit();
        update.changes().txnAbort();
    }

    @Test
    public void givenTxnConsumers_whenStartingOperation_thenUpdateCtlCallsConsumers() {
        // Given
        Consumer<HttpAction> onBegin = mock(Consumer.class);
        Consumer<HttpAction> onCommit = mock(Consumer.class);
        Consumer<HttpAction> onAbort = mock(Consumer.class);
        HttpAction action = mock(HttpAction.class);
        Context context = new Context();
        when(action.getContext()).thenReturn(context);
        DatasetGraph dsg = mock(DatasetGraph.class);

        // When
        CQRS.UpdateCQRS update =
                CQRS.startOperation(KafkaTestCluster.DEFAULT_TOPIC, null, action, dsg, onBegin, onCommit, onAbort);

        // Then
        verify(onBegin, times(1)).accept(any());
        verifyNoInteractions(onCommit);
        verifyNoInteractions(onAbort);
        update.changes().txnCommit();
        update.changes().txnAbort();
        verify(onCommit, times(1)).accept(any());
        verify(onAbort, times(1)).accept(any());
    }

    @Test
    public void givenDefaultTxnConsumers_whenAbortingOperation_thenAborted() {
        // Given
        HttpAction action = mock(HttpAction.class);
        Context context = new Context();
        when(action.getContext()).thenReturn(context);
        DatasetGraph dsg = mock(DatasetGraph.class);

        // When
        CQRS.UpdateCQRS update =
                CQRS.startOperation(KafkaTestCluster.DEFAULT_TOPIC, null, action, dsg, CQRS.onBegin, CQRS.onCommit, CQRS.onAbort);
        update.changes().txnAbort();

        // Then
    }

    @Test
    public void givenActionWithoutProducer_whenCommitting_thenNoOp() throws IOException {
        // Given
        ActionService service = CQRS.updateAction(KafkaTestCluster.DEFAULT_TOPIC, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);

        // When and Then
        service.execute(action);
    }

    @Test
    public void givenActionWithProducer_whenServicingRequest_thenKafkaMessageProduced() throws IOException {
        // Given
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        ActionService service = CQRS.updateActionWithProducer(KafkaTestCluster.DEFAULT_TOPIC, producer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);

        // When
        service.execute(action);

        // Then
        Assertions.assertEquals(1, producer.history().size());
    }

    @Test
    public void givenActionWithProducer_whenServicingRequestWithHeaders_thenKafkaMessageProducedWithHeaders() throws IOException {
        // Given
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        ActionService service = CQRS.updateActionWithProducer(KafkaTestCluster.DEFAULT_TOPIC, producer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(TelicentHeaders.SECURITY_LABEL)).thenReturn("employee");
        when(request.getHeader(TelicentHeaders.DISTRIBUTION_ID)).thenReturn("https://example.org/distro/1");
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);

        // When
        service.execute(action);

        // Then
        Assertions.assertEquals(1, producer.history().size());
        ProducerRecord<String, byte[]> event = producer.history().getFirst();
        Assertions.assertNotNull(event.headers().lastHeader(TelicentHeaders.SECURITY_LABEL));
        Assertions.assertNotNull(event.headers().lastHeader(TelicentHeaders.DISTRIBUTION_ID));
    }

    @Test
    public void givenActionWithProducer_whenProducerFails_thenFails_andNoKafkaMessageProduced() throws IOException {
        // Given
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        producer.sendException = new KafkaException("Failed to connect to Kafka");
        ActionService service = CQRS.updateActionWithProducer(KafkaTestCluster.DEFAULT_TOPIC, producer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);

        // When
        ActionErrorException actionError = Assertions.assertThrows(ActionErrorException.class, () -> service.execute(action));

        // Then
        Assertions.assertEquals(HttpSC.INTERNAL_SERVER_ERROR_500, actionError.getRC());

        // And
        Assertions.assertEquals(0, producer.history().size());
    }

    @Test
    public void givenActionWithProducer_whenProducerInterrupted_thenFails() throws IOException {
        // Given
        Producer<String, byte[]> producer =
                mock(Producer.class);
        when(producer.send(any())).thenAnswer(invocation -> {
            throw new InterruptedException();
        });
        ActionService service = CQRS.updateActionWithProducer(KafkaTestCluster.DEFAULT_TOPIC, producer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);

        // When
        ActionErrorException actionError = Assertions.assertThrows(ActionErrorException.class, () -> service.execute(action));

        // Then
        Assertions.assertEquals(HttpSC.INTERNAL_SERVER_ERROR_500, actionError.getRC());
    }

    public static Stream<Arguments> cqrsFailures() {
        return Stream.of(Arguments.of(new UpdateException(), HttpSC.INTERNAL_SERVER_ERROR_500),
                         Arguments.of(new QueryParseException("Malformed SPARQL Update", 1, 1), HttpSC.BAD_REQUEST_400),
                         Arguments.of(new OperationDeniedException("Forbidden"), HttpSC.INTERNAL_SERVER_ERROR_500),
                         Arguments.of(new RuntimeException("Failed"), HttpSC.INTERNAL_SERVER_ERROR_500),
                         Arguments.of(new ActionErrorException(HttpSC.UNAUTHORIZED_401, "Unauthorized", null), HttpSC.UNAUTHORIZED_401));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("cqrsFailures")
    public void givenCQRSFails_whenProcessingRequest_thenHttpErrorResponse(Exception e, int expectedStatus) throws
            IOException {
        // Given
        ActionService service = CQRS.updateAction(KafkaTestCluster.DEFAULT_TOPIC, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = mock(ServletInputStream.class);
        when(request.getInputStream()).thenReturn(input);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(request.getServletContext()).thenReturn(servletContext);
        Logger logger = mock(Logger.class);
        HttpAction action = new HttpAction(1, logger, ActionCategory.ACTION, request, response);
        Endpoint endpoint = mock(Endpoint.class);
        CounterSet counters = mock(CounterSet.class);
        when(endpoint.getCounters()).thenReturn(counters);
        action.setEndpoint(endpoint);

        try (MockedStatic<CQRS> cqrs = mockStatic(CQRS.class)) {
            cqrs.when(() -> CQRS.startOperation(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(e);

            // When
            Exception thrown =
                    Assertions.assertThrows(Exception.class, () -> service.execute(action));

            // Then
            if (thrown instanceof ActionErrorException actionError) {
                Assertions.assertEquals(expectedStatus, actionError.getRC());
            }
        }
    }
}
