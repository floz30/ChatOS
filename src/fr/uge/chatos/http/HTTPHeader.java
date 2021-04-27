package fr.uge.chatos.http;

import static fr.uge.chatos.http.HTTPException.ensure;

import java.util.*;

/**
 * This class collects the information on a HTTP header.
 */

public class HTTPHeader {
    
    private static final String[] LIST_SUPPORTED_VERSIONS = new String[]{"HTTP/1.1"};
    public static final Set<String> SUPPORTED_VERSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(LIST_SUPPORTED_VERSIONS)));


    private final String response;
    private final String version;
    private final int code;
    private final Map<String, String> fields;


    private HTTPHeader(String response, String version, int code, Map<String, String> fields) throws HTTPException {
        this.response = response;
        this.version = version;
        this.code = code;
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static HTTPHeader create(String response, Map<String,String> fields) throws HTTPException {
        String[] tokens = response.split(" ");
        // Treatment of the response line
        ensure(tokens.length >= 2, "Badly formed response:\n" + response);
        String version = tokens[0];
        ensure(HTTPHeader.SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);
        int code = 0;
        try {
            code = Integer.parseInt(tokens[1]);
            ensure(code >= 100 && code < 600, "Invalid code in response:\n" + response);
        } catch (NumberFormatException e) {
            ensure(false, "Invalid response:\n" + response);
        }
        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s.toLowerCase(),fields.get(s).trim());
        return new HTTPHeader(response,version,code,fieldsCopied);
    }

    public String getResponse() {
        return response;
    }

    public String getVersion() {
        return version;
    }

    public int getCode() {
        return code;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * @return the value of the Content-Length field in the header
     *         -1 if the field does not exists
     * @throws HTTPException when the value of Content-Length is not a number
     */
    public int getContentLength() throws HTTPException {
        String s = fields.get("content-length");
        if (s == null) return -1;
        else {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new HTTPException("Invalid Content-Length field value :\n" + s);
            }
        }
    }

    /**
     * @return the Content-Type
     *         null if there is no Content-Type field
     */
    public String getContentType() {
        String s = fields.get("content-type");
        if (s != null) {
            return s.split(";")[0].trim();
        } else
            return null;
    }


}