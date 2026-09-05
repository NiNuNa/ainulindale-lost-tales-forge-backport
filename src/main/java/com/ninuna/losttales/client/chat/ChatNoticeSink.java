package com.ninuna.losttales.client.chat;

/**
 * Where a collaborator of the chat screen says something short to the
 * player — a refused insertion, a message too long to send — without
 * owning the notice that says it. The screen draws the notice over the
 * input bar.
 */
interface ChatNoticeSink {
    void showNotice(String message);
}
