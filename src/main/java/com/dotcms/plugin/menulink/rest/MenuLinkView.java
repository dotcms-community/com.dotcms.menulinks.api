package com.dotcms.plugin.menulink.rest;

import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.VersionInfo;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.links.model.Link;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.UtilMethods;

/**
 * JSON representation of a Menu Link returned by the REST API.
 *
 * <p>Built via {@link #from(Link)} which enriches the raw {@link Link} model with
 * live/working state from {@code VersionInfo} and the folder path from {@code Identifier}.
 */
public class MenuLinkView {

    private final String inode;
    private final String identifier;
    private final String title;
    private final String friendlyName;
    private final String url;
    /** Canonical spelling exposed in the API (the model field is misspelled as {@code protocal}). */
    private final String protocol;
    private final String target;
    private final String linkType;
    private final String linkCode;
    private final String internalLinkIdentifier;
    private final boolean showOnMenu;
    private final int sortOrder;
    private final boolean live;
    private final boolean working;
    private final String siteId;
    private final String folderPath;

    public MenuLinkView(
            final String inode,
            final String identifier,
            final String title,
            final String friendlyName,
            final String url,
            final String protocol,
            final String target,
            final String linkType,
            final String linkCode,
            final String internalLinkIdentifier,
            final boolean showOnMenu,
            final int sortOrder,
            final boolean live,
            final boolean working,
            final String siteId,
            final String folderPath) {

        this.inode                  = inode;
        this.identifier             = identifier;
        this.title                  = title;
        this.friendlyName           = friendlyName;
        this.url                    = url;
        this.protocol               = protocol;
        this.target                 = target;
        this.linkType               = linkType;
        this.linkCode               = linkCode;
        this.internalLinkIdentifier = internalLinkIdentifier;
        this.showOnMenu             = showOnMenu;
        this.sortOrder              = sortOrder;
        this.live                   = live;
        this.working                = working;
        this.siteId                 = siteId;
        this.folderPath             = folderPath;
    }

    /**
     * Builds a {@link MenuLinkView} from a {@link Link}, fetching live/working state
     * and folder path from the system APIs.
     */
    public static MenuLinkView from(final Link link) throws Exception {
        final String identifier = link.getIdentifier();

        boolean isLive    = false;
        boolean isWorking = false;
        String  siteId    = UtilMethods.isSet(link.getHostId()) ? link.getHostId() : "";
        String  folderPath = "";

        if (UtilMethods.isSet(identifier)) {
            final VersionInfo vi = APILocator.getVersionableAPI().getVersionInfo(identifier);
            if (vi != null) {
                final String liveInode    = vi.getLiveInode();
                final String workingInode = vi.getWorkingInode();
                isLive    = InodeUtils.isSet(liveInode)    && liveInode.equals(link.getInode());
                isWorking = InodeUtils.isSet(workingInode) && workingInode.equals(link.getInode());
            }

            final Identifier ident = APILocator.getIdentifierAPI().find(identifier);
            if (ident != null && UtilMethods.isSet(ident.getId())) {
                folderPath = ident.getParentPath();
                if (UtilMethods.isSet(ident.getHostId())) {
                    siteId = ident.getHostId();
                }
            }
        }

        return new MenuLinkView(
                link.getInode(),
                identifier,
                link.getTitle(),
                link.getFriendlyName(),
                link.getUrl(),
                link.getProtocal(),      // model field is misspelled; we expose it correctly
                link.getTarget(),
                link.getLinkType(),
                link.getLinkCode(),
                link.getInternalLinkIdentifier(),
                link.isShowOnMenu(),
                link.getSortOrder(),
                isLive,
                isWorking,
                siteId,
                folderPath);
    }

    public String  getInode()                  { return inode; }
    public String  getIdentifier()             { return identifier; }
    public String  getTitle()                  { return title; }
    public String  getFriendlyName()           { return friendlyName; }
    public String  getUrl()                    { return url; }
    public String  getProtocol()              { return protocol; }
    public String  getTarget()                { return target; }
    public String  getLinkType()              { return linkType; }
    public String  getLinkCode()              { return linkCode; }
    public String  getInternalLinkIdentifier(){ return internalLinkIdentifier; }
    public boolean isShowOnMenu()             { return showOnMenu; }
    public int     getSortOrder()             { return sortOrder; }
    public boolean isLive()                   { return live; }
    public boolean isWorking()                { return working; }
    public String  getSiteId()                { return siteId; }
    public String  getFolderPath()            { return folderPath; }
}
