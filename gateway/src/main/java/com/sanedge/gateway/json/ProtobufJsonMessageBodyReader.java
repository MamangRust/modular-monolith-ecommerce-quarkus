package com.sanedge.gateway.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;

/**
 * RESTEasy/Jackson does not know how to construct generated protobuf messages.
 * Use protobuf's canonical JSON parser for command request bodies instead.
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Priority(Priorities.ENTITY_CODER)
public class ProtobufJsonMessageBodyReader implements MessageBodyReader<Message> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Message.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Message readFrom(Class<Message> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream) throws IOException, WebApplicationException {
        try (InputStreamReader reader = new InputStreamReader(entityStream, StandardCharsets.UTF_8)) {
            Method newBuilder = type.getMethod("newBuilder");
            Message.Builder builder = (Message.Builder) newBuilder.invoke(null);
            JsonFormat.parser().merge(reader, builder);
            return builder.build();
        } catch (ReflectiveOperationException | IOException e) {
            throw new WebApplicationException(
                    "Invalid protobuf JSON payload: " + e.getMessage(),
                    e,
                    Response.Status.BAD_REQUEST);
        }
    }
}
