/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

public final class ServiceInfoEntity {
    private String portName;
    private String portLogo;

    private ServiceInfoEntity() {
    }

    public ServiceInfoEntity(String name, String logo) {
        this.portName = name;
        this.portLogo = logo;
    }

    public String getPortName() {
        return portName;
    }

    public String getPortLogo() {
        return portLogo;
    }
}