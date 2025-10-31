package org.twinlife.twinlife;

public final class ServerConfig {
    public static final String DOMAIN = "twin.me";
    public static final String URL = "ws.twin.me";

    private ServerConfig(){
        throw new AssertionError("Not instantiable");
    }
}
