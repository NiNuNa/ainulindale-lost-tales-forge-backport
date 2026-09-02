package com.ninuna.losttales.client.skin;

/**
 * What an account's profile says about its skin: where the texture is, the
 * hash that names it, and whether the account chose the slim arm model.
 */
public final class AccountSkinProfile {

    private final String url;
    private final String hash;
    private final boolean slim;

    AccountSkinProfile(String url, String hash, boolean slim) {
        if (url == null || url.length() == 0 || hash == null || hash.length() == 0) {
            throw new IllegalArgumentException("skin url and hash must not be blank");
        }
        this.url = url;
        this.hash = hash;
        this.slim = slim;
    }

    public String getUrl() {
        return this.url;
    }

    /** The texture's own name, the last path segment of the URL. */
    public String getHash() {
        return this.hash;
    }

    public boolean isSlim() {
        return this.slim;
    }
}
