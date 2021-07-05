package com.kungeek.seleniumplus.pool;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.remote.http.*;

import java.io.IOException;
import java.net.URL;

public class DepHttpCommandExecutor extends HttpCommandExecutor {
    private static final HttpClient.Factory defaultClientFactory = HttpClient.Factory.createDefault();
    private final HttpClient.Factory httpClientFactory;
    private final HttpClient client;
    private CommandCodec<HttpRequest> commandCodec = new W3CHttpCommandCodec();
    private ResponseCodec<HttpResponse> responseCodec =  new W3CHttpResponseCodec();


    DepHttpCommandExecutor(URL addressOfRemoteServer) {
        super(addressOfRemoteServer);
        this.httpClientFactory = defaultClientFactory;
        this.client = httpClientFactory.createClient(addressOfRemoteServer);
    }


    @Override
    public Response execute(Command command) throws IOException {
        if (command.getSessionId() == null) {
            if ("quit".equals(command.getName())) {
                return new Response();
            }

            if (!"getAllSessions".equals(command.getName()) && !"newSession".equals(command.getName())) {
                throw new NoSuchSessionException("Session ID is null. Using WebDriver after calling quit()?");
            }
        }
        if (!"newSession".equals(command.getName())) {
            if (this.commandCodec != null && this.responseCodec != null) {
                HttpRequest httpRequest = this.commandCodec.encode(command);

                try {
                    HttpResponse httpResponse = this.client.execute(httpRequest);
                    Response response = this.responseCodec.decode(httpResponse);
                    if (response.getSessionId() == null) {
                        if (httpResponse.getTargetHost() != null) {
                            response.setSessionId(HttpSessionId.getSessionId(httpResponse.getTargetHost()));
                        } else {
                            response.setSessionId(command.getSessionId().toString());
                        }
                    }

                    if ("quit".equals(command.getName())) {
                        this.httpClientFactory.cleanupIdleClients();
                    }

                    return response;
                } catch (UnsupportedCommandException var7) {
                    if (var7.getMessage() != null && !"".equals(var7.getMessage())) {
                        throw var7;
                    } else {
                        throw new UnsupportedOperationException("No information from server. Command name was: " + command.getName(), var7.getCause());
                    }
                }
            } else {
                throw new WebDriverException("No command or response codec has been defined. Unable to proceed");
            }
        } else if (this.commandCodec != null) {
            throw new SessionNotCreatedException("Session already exists");
        } else {
            super.execute(command);
        }
        return null;
    }
}
