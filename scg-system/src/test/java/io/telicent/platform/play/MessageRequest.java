/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.platform.play;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.atlas.lib.Bytes;

/**
 * A message is a set of headers then a body.
 * One format is a as a message file:
 * <pre>
 * Headers
 * -- blank line --
 * body
 * </pre>
 * The library does parse the headers because the JDK library accepts headers as a map
 * but the the parsing is not full error checking.
 */
public class MessageRequest {

    public static MessageRequest fromFile(Path path) {
        return fromFile(path.toString());
    }

    /**
     * Create a {@code MessageRequest} by reading a file in message request format.
     */
    public static MessageRequest fromFile(String filename) {
        try ( InputStream input = IO.openFileBuffered(filename) ) {
            return fromInputStream(input);
        } catch (IOException ex) {
            throw IOX.exception(ex);
        }
    }

    /**
     * Create a {@code MessageRequest} by reading an input stream in the messge
     * request format.
     */
    public static MessageRequest fromInputStream(InputStream input) {
        Map<String, String> headers = readHeaders(input);
        // Consume the body to give simple interface.
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(input);
            return new MessageRequest(headers, bytes);
        } catch (IOException ex) {
            throw IOX.exception(ex);
        }
    }

    /**
     * Create a {@code MessageRequest} with a body from the byte contents of a file
     * and map of headers.
     */
    public static MessageRequest create(String filename, Map<String, String> headers) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filename));
            return new MessageRequest(headers, bytes);
        } catch (IOException e) {
            throw IOX.exception(e);
        }
    }

    private final Map<String, String> headers;

    private final InputStream bodySource;

    MessageRequest(Map<String, String> headers, String body) {
        this(headers, Bytes.asUTF8bytes(body));
    }

    MessageRequest(Map<String, String> headers, byte[] bytes) {
        this(headers, new ByteArrayInputStream(bytes));
    }

    MessageRequest(Map<String, String> headers, InputStream body) {
        this.headers = headers;
        this.bodySource = body;
    }


    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Return an {@link InputStream} of the bytes of the body.
     */
    public InputStream getBody() {
        return bodySource;
    }


    /**
    * Return the bytes of the body as a byte array.
    * Modifying the array will not change the message request.
    */
    public byte[] getBodyBytes() {
        try {
            return IOUtils.toByteArray(bodySource);
        } catch (IOException e) {
            throw IOX.exception(e);
        }
    }

    /* Parse headers - stop at first blank line */
    private static Map<String, String> readHeaders(InputStream input) {
        Map<String, String> headers = new HashMap<>();
        // Read until blank line.
        byte[] line = new byte[1000];
        for ( ;; ) {
            int x = readLine(input, line);
            if ( x == -1 )
                break;
            if ( x == 1 && line[0] == '\n' ) {
                break;
            }
            // Exclude the final newline.
            String header = new String(line, 0, x-1, StandardCharsets.UTF_8);
            accumulateHeader(headers, header);
        }
        return headers;
    }

    private static void accumulateHeader(Map<String, String> headers, String header) {
        int idx = header.indexOf(':');
        if ( idx < 0 )
            throw new DataException("Bad HTTP header: "+header);
        String h = header.substring(0,idx).strip();
        String v = header.substring(idx+1, header.length()).strip();
        headers.put(h, v);
    }


    /** Read a line - return bytesRead */
    private static int readLine(InputStream input, byte[] line) {
        try {
            return readLine$(input, line);
        } catch (IOException ex) {
            throw IOX.exception(ex);
        }
    }

    private static int readLine$(InputStream input, byte[] line) throws IOException {
        final int N = line.length;
        int i = 0;
        int bytesRead = 0;
        for(;;) {
            int x;
            x = input.read();
            if ( x == -1 )
                return bytesRead==0 ? -1 : bytesRead;
            bytesRead++;
            line[i++] = (byte)x;
            if ( x == '\n' )
                break;
            if ( i >= N )
                break;
        }
        return bytesRead;
    }

}
