package com.sam.syncai;

public final class FileAttachment {
    public final String name;
    public final String mimeType;
    public final String content;
    public final boolean textReadable;

    public FileAttachment(String name, String mimeType, String content, boolean textReadable) {
        this.name = name;
        this.mimeType = mimeType;
        this.content = content;
        this.textReadable = textReadable;
    }

    public String promptBlock() {
        if (!textReadable) {
            return "ATTACHMENT: " + name + " (" + mimeType + ")\n"
                    + "[Binary/non-text attachment. The local text model can see the filename and type, "
                    + "but not its binary contents yet.]";
        }
        return "ATTACHMENT: " + name + " (" + mimeType + ")\n"
                + content;
    }
}
