package org.evan.ai.audit.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from GitHub Device Flow authorization request.
 */
public class DeviceCodeResponse {

    @JsonProperty("device_code")
    private String deviceCode;

    @JsonProperty("user_code")
    private String userCode;

    @JsonProperty("verification_uri")
    private String verificationUri;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("interval")
    private int interval;

    public DeviceCodeResponse() {
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public void setVerificationUri(String verificationUri) {
        this.verificationUri = verificationUri;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * Get the full verification URL with the user code.
     */
    public String getVerificationUriComplete() {
        if (verificationUri != null && userCode != null) {
            return verificationUri + "?code=" + userCode;
        }
        return verificationUri;
    }

    @Override
    public String toString() {
        return "DeviceCodeResponse{" +
                "deviceCode='" + deviceCode + '\'' +
                ", userCode='" + userCode + '\'' +
                ", verificationUri='" + verificationUri + '\'' +
                ", expiresIn=" + expiresIn +
                ", interval=" + interval +
                '}';
    }
}
