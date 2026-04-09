package com.dotcms.plugin.menulink.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for creating or updating a Menu Link.
 *
 * <p>For create ({@code POST}), both {@code siteId} and {@code folderPath} are required.
 * For update ({@code PUT}), omit them to keep the link in its current folder, or supply
 * them both to move the link at the same time as editing it.
 *
 * <p>{@code linkType} must be one of {@code EXTERNAL} (default), {@code INTERNAL}, or
 * {@code CODE}.  Supply the matching companion field ({@code url}, {@code internalLinkIdentifier},
 * or {@code linkCode}) accordingly.
 */
public class MenuLinkForm {

    private final String title;
    private final String friendlyName;
    private final String url;
    /** Exposed as {@code protocol} in the API; stored as {@code protocal} in the model (legacy typo). */
    private final String protocol;
    private final String target;
    /** One of EXTERNAL, INTERNAL, CODE. Defaults to EXTERNAL when not supplied. */
    private final String linkType;
    private final String linkCode;
    private final String internalLinkIdentifier;
    private final boolean showOnMenu;
    private final int sortOrder;
    /** Host identifier (UUID) or hostname. Required for create; optional for update (move). */
    private final String siteId;
    /** Folder path on the site, e.g. {@code "/"} or {@code "/about"}. Required for create. */
    private final String folderPath;
    /** When {@code true} the link is published (made live) immediately after saving. */
    private final boolean publish;

    @JsonCreator
    public MenuLinkForm(
            @JsonProperty("title")                   final String title,
            @JsonProperty("friendlyName")             final String friendlyName,
            @JsonProperty("url")                      final String url,
            @JsonProperty("protocol")                 final String protocol,
            @JsonProperty("target")                   final String target,
            @JsonProperty("linkType")                 final String linkType,
            @JsonProperty("linkCode")                 final String linkCode,
            @JsonProperty("internalLinkIdentifier")   final String internalLinkIdentifier,
            @JsonProperty("showOnMenu")               final boolean showOnMenu,
            @JsonProperty("sortOrder")                final int sortOrder,
            @JsonProperty("siteId")                   final String siteId,
            @JsonProperty("folderPath")               final String folderPath,
            @JsonProperty("publish")                  final boolean publish) {

        this.title                   = title;
        this.friendlyName            = friendlyName;
        this.url                     = url;
        this.protocol                = protocol;
        this.target                  = target;
        this.linkType                = linkType;
        this.linkCode                = linkCode;
        this.internalLinkIdentifier  = internalLinkIdentifier;
        this.showOnMenu              = showOnMenu;
        this.sortOrder               = sortOrder;
        this.siteId                  = siteId;
        this.folderPath              = folderPath;
        this.publish                 = publish;
    }

    public String getTitle()                  { return title; }
    public String getFriendlyName()           { return friendlyName; }
    public String getUrl()                    { return url; }
    public String getProtocol()              { return protocol; }
    public String getTarget()                { return target; }
    public String getLinkType()              { return linkType; }
    public String getLinkCode()              { return linkCode; }
    public String getInternalLinkIdentifier(){ return internalLinkIdentifier; }
    public boolean isShowOnMenu()            { return showOnMenu; }
    public int getSortOrder()                { return sortOrder; }
    public String getSiteId()                { return siteId; }
    public String getFolderPath()            { return folderPath; }
    public boolean isPublish()               { return publish; }
}
