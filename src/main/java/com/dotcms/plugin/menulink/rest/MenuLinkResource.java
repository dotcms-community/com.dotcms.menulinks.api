package com.dotcms.plugin.menulink.rest;

import com.dotcms.rest.InitDataObject;
import com.dotcms.rest.ResponseEntityView;
import com.dotcms.rest.WebResource;
import com.dotcms.rest.annotation.NoCache;
import com.dotcms.rest.api.v1.authentication.ResponseUtil;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.db.DotConnect;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.links.model.Link;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for managing dotCMS Menu Links (the {@code Link} web-asset type).
 *
 * <p>Base path: {@code /api/v1/menulinks}
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET    /api/v1/menulinks}                    — list links</li>
 *   <li>{@code GET    /api/v1/menulinks/{identifier}}       — get one link</li>
 *   <li>{@code POST   /api/v1/menulinks}                    — create a link</li>
 *   <li>{@code PUT    /api/v1/menulinks/{identifier}}       — update a link</li>
 *   <li>{@code DELETE /api/v1/menulinks/{identifier}}       — delete a link</li>
 *   <li>{@code PUT    /api/v1/menulinks/{identifier}/_publish}   — publish (make live)</li>
 *   <li>{@code PUT    /api/v1/menulinks/{identifier}/_unpublish} — unpublish</li>
 * </ul>
 */
@Path("/v1/menulinks")
@Tag(name = "Menu Links", description = "CRUD operations for dotCMS Menu Link entities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MenuLinkResource {

    private final WebResource webResource;

    public MenuLinkResource() {
        this(new WebResource());
    }

    MenuLinkResource(final WebResource webResource) {
        this.webResource = webResource;
    }

    // -------------------------------------------------------------------------
    // LIST
    // -------------------------------------------------------------------------

    @GET
    @NoCache
    @Operation(
            operationId = "listMenuLinks",
            summary     = "List menu links",
            description = "Returns a paginated list of menu links the authenticated user can read. "
                    + "Filter by site and/or folder path.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List retrieved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = "object",
                                    description = "ResponseEntityView wrapping a list of MenuLinkView objects"))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public Response list(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Host identifier (UUID) or hostname to filter by")
            @QueryParam("siteId") final String siteId,
            @Parameter(description = "Parent folder path to filter by (e.g. /about)", allowReserved = true)
            @QueryParam("folderPath") final String folderPath,
            @Parameter(description = "Include archived links")
            @QueryParam("includeArchived") @DefaultValue("false") final boolean includeArchived,
            @Parameter(description = "Zero-based offset for pagination")
            @QueryParam("offset") @DefaultValue("0") final int offset,
            @Parameter(description = "Maximum number of results. Defaults to 50. Pass 0 to return all results.")
            @QueryParam("limit") @DefaultValue("50") final int limit,
            @Parameter(description = "Field(s) to sort by. Accepts a single value or a comma-separated list "
                    + "for multi-level sorting, e.g. `path,title` or `path,sort_order`. "
                    + "Accepted values (case- and underscore-insensitive): "
                    + "`title` (default), `friendly_name`, `url`, `target`, `link_type`, `link_code`, "
                    + "`sort_order`, `mod_date`, `mod_user`, `show_on_menu`, `path`. "
                    + "`path` sorts by each link's parent folder path; it is only meaningful when "
                    + "`depth` > 0 causes links from multiple folders to appear in the same result set. "
                    + "When the separate `folderPath` parameter is specified, sorting is applied in-memory and all comma-separated "
                    + "tokens take effect. Otherwise only the first token is passed directly to the database "
                    + "as a column name; additional tokens are ignored.")
            @QueryParam("orderBy") @DefaultValue("title") final String orderBy,
            @Parameter(description = "Folder traversal depth when folderPath is specified. "
                    + "0 = only the specified folder (default), 1 = include direct child folders, "
                    + "2 = grandchildren, and so on. "
                    + "Note: larger depths may be computationally expensive on trees with many nested subfolders.")
            @QueryParam("depth") @DefaultValue("0") final int depth) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            String resolvedHostId = null;

            if (UtilMethods.isSet(siteId)) {
                final Host site = resolveHost(siteId, user);
                resolvedHostId = site.getIdentifier();
            }

            final List<Link> links;
            if (UtilMethods.isSet(folderPath)) {
                if (!UtilMethods.isSet(resolvedHostId)) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ResponseEntityView<>("siteId is required when folderPath is specified"))
                            .build();
                }
                final Host site = APILocator.getHostAPI().find(resolvedHostId, user, false);
                final Folder folder = APILocator.getFolderAPI()
                        .findFolderByPath(folderPath, site, user, false);
                if (folder == null || !InodeUtils.isSet(folder.getInode())) {
                    return Response.ok(new ResponseEntityView<>(java.util.Collections.emptyList())).build();
                }
                // Uses a two-query SQL approach (one DotConnect pass to resolve inodes,
                // one Hibernate batch load) instead of one findFolderMenuLinks() +
                // one findSubFolders() call per folder. Re-apply permission filtering
                // since the SQL runs without user context.
                final List<Link> folderLinks = findLinksUnderPath(folderPath, site, Math.max(0, depth));
                List<Link> permitted = APILocator.getPermissionAPI()
                        .filterCollection(folderLinks, PermissionAPI.PERMISSION_READ, false, user);

                if (!includeArchived) {
                    permitted = permitted.stream()
                            .filter(l -> {
                                try {
                                    return !l.isArchived();
                                } catch (Exception e) {
                                    Logger.warn(this, "Could not check archived state for link inode=" + l.getInode() + ": " + e.getMessage());
                                    return true;
                                }
                            })
                            .collect(Collectors.toList());
                }

                permitted = sortLinks(permitted, orderBy);

                final int fromIndex = Math.min(offset, permitted.size());
                final int toIndex = limit > 0
                        ? Math.min(fromIndex + limit, permitted.size())
                        : permitted.size();
                links = permitted.subList(fromIndex, toIndex);
            } else {
                links = APILocator.getMenuLinkAPI().findLinks(
                        user,
                        includeArchived,
                        null,
                        resolvedHostId,
                        null,
                        null,
                        null,
                        offset,
                        limit == 0 ? Integer.MAX_VALUE : limit,
                        orderBy);
            }

            final List<MenuLinkView> views = links.stream()
                    .map(link -> {
                        try {
                            return MenuLinkView.from(link);
                        } catch (Exception e) {
                            Logger.warn(this, "Could not build view for link inode=" + link.getInode(), e);
                            return null;
                        }
                    })
                    .filter(v -> v != null)
                    .collect(Collectors.toList());

            return Response.ok(new ResponseEntityView<>(views)).build();

        } catch (Exception e) {
            Logger.error(this, "Error listing menu links: " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // GET ONE
    // -------------------------------------------------------------------------

    @GET
    @Path("/{identifier}")
    @NoCache
    @Operation(
            operationId = "getMenuLink",
            summary     = "Get a menu link by identifier",
            description = "Returns the working version of the menu link with the given identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = "object",
                                    description = "ResponseEntityView wrapping a MenuLinkView"))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Link not found")
    })
    public Response get(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Menu link identifier (UUID)", required = true)
            @PathParam("identifier") final String identifier) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            final Link link = APILocator.getMenuLinkAPI()
                    .findWorkingLinkById(identifier, user, false);

            if (link == null || !InodeUtils.isSet(link.getInode())) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseEntityView<>("Menu link not found: " + identifier))
                        .build();
            }

            return Response.ok(new ResponseEntityView<>(MenuLinkView.from(link))).build();

        } catch (Exception e) {
            Logger.error(this, "Error fetching menu link " + identifier + ": " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @POST
    @Operation(
            operationId = "createMenuLink",
            summary     = "Create a menu link",
            description = "Creates a new menu link in the specified site and folder. "
                    + "Both `siteId` and `folderPath` are required. "
                    + "Set `publish: true` to make the link live immediately.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = "object",
                                    description = "ResponseEntityView wrapping the created MenuLinkView"))),
            @ApiResponse(responseCode = "400", description = "Missing required fields"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public Response create(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @RequestBody(description = "Menu link to create", required = true)
            final MenuLinkForm form) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            if (!UtilMethods.isSet(form.getTitle())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ResponseEntityView<>("title is required"))
                        .build();
            }
            if (!UtilMethods.isSet(form.getSiteId())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ResponseEntityView<>("siteId is required"))
                        .build();
            }
            if (!UtilMethods.isSet(form.getFolderPath())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ResponseEntityView<>("folderPath is required"))
                        .build();
            }

            validateLinkTypeFields(form);

            final Host site = resolveHost(form.getSiteId(), user);
            final Folder folder = resolveFolder(form.getFolderPath(), site, user);

            final Link link = buildLink(form);
            // parent must be set before save so dotCMS creates the tree table entry
            // that associates the link with its folder. Without it the UI folder view breaks.
            link.setParent(folder.getInode());
            APILocator.getMenuLinkAPI().save(link, folder, user, false);

            if (form.isPublish()) {
                APILocator.getVersionableAPI().setLive(link);
            }

            return Response.ok(new ResponseEntityView<>(MenuLinkView.from(link))).build();

        } catch (Exception e) {
            Logger.error(this, "Error creating menu link: " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @PUT
    @Path("/{identifier}")
    @Operation(
            operationId = "updateMenuLink",
            summary     = "Update a menu link",
            description = "Updates the working version of an existing menu link. "
                    + "Supply `siteId` + `folderPath` to move the link to a different folder. "
                    + "Set `publish: true` to publish the updated link immediately.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = "object",
                                    description = "ResponseEntityView wrapping the updated MenuLinkView"))),
            @ApiResponse(responseCode = "400", description = "Missing required fields"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Link not found")
    })
    public Response update(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Menu link identifier (UUID)", required = true)
            @PathParam("identifier") final String identifier,
            @RequestBody(description = "Updated menu link fields", required = true)
            final MenuLinkForm form) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            final Link existing = APILocator.getMenuLinkAPI()
                    .findWorkingLinkById(identifier, user, false);

            if (existing == null || !InodeUtils.isSet(existing.getInode())) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseEntityView<>("Menu link not found: " + identifier))
                        .build();
            }

            applyForm(form, existing);

            final boolean move = UtilMethods.isSet(form.getSiteId())
                    && UtilMethods.isSet(form.getFolderPath());

            if (move) {
                final Host site     = resolveHost(form.getSiteId(), user);
                final Folder folder = resolveFolder(form.getFolderPath(), site, user);
                existing.setParent(folder.getInode());
                APILocator.getMenuLinkAPI().save(existing, folder, user, false);
            } else {
                APILocator.getMenuLinkAPI().save(existing, user, false);
            }

            if (form.isPublish()) {
                APILocator.getVersionableAPI().setLive(existing);
            }

            return Response.ok(new ResponseEntityView<>(MenuLinkView.from(existing))).build();

        } catch (Exception e) {
            Logger.error(this, "Error updating menu link " + identifier + ": " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @DELETE
    @Path("/{identifier}")
    @Operation(
            operationId = "deleteMenuLink",
            summary     = "Delete a menu link",
            description = "Permanently deletes all versions of the specified menu link.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Link not found")
    })
    public Response delete(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Menu link identifier (UUID)", required = true)
            @PathParam("identifier") final String identifier) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            final Link link = APILocator.getMenuLinkAPI()
                    .findWorkingLinkById(identifier, user, false);

            if (link == null || !InodeUtils.isSet(link.getInode())) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseEntityView<>("Menu link not found: " + identifier))
                        .build();
            }

            APILocator.getMenuLinkAPI().delete(link, user, false);

            return Response.ok(new ResponseEntityView<>("Menu link deleted: " + identifier)).build();

        } catch (Exception e) {
            Logger.error(this, "Error deleting menu link " + identifier + ": " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // PUBLISH
    // -------------------------------------------------------------------------

    @PUT
    @Path("/{identifier}/_publish")
    @Operation(
            operationId = "publishMenuLink",
            summary     = "Publish a menu link",
            description = "Makes the working version of the menu link live.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link published"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Link not found")
    })
    public Response publish(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Menu link identifier (UUID)", required = true)
            @PathParam("identifier") final String identifier) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            final Link link = APILocator.getMenuLinkAPI()
                    .findWorkingLinkById(identifier, user, false);

            if (link == null || !InodeUtils.isSet(link.getInode())) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseEntityView<>("Menu link not found: " + identifier))
                        .build();
            }

            APILocator.getVersionableAPI().setLive(link);

            return Response.ok(new ResponseEntityView<>(MenuLinkView.from(link))).build();

        } catch (Exception e) {
            Logger.error(this, "Error publishing menu link " + identifier + ": " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // UNPUBLISH
    // -------------------------------------------------------------------------

    @PUT
    @Path("/{identifier}/_unpublish")
    @Operation(
            operationId = "unpublishMenuLink",
            summary     = "Unpublish a menu link",
            description = "Removes the menu link from live; the working version is retained.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link unpublished"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Link not found")
    })
    public Response unpublish(
            @Context final HttpServletRequest  request,
            @Context final HttpServletResponse response,
            @Parameter(description = "Menu link identifier (UUID)", required = true)
            @PathParam("identifier") final String identifier) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            final Link link = APILocator.getMenuLinkAPI()
                    .findWorkingLinkById(identifier, user, false);

            if (link == null || !InodeUtils.isSet(link.getInode())) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ResponseEntityView<>("Menu link not found: " + identifier))
                        .build();
            }

            APILocator.getVersionableAPI().removeLive(link.getIdentifier());

            return Response.ok(new ResponseEntityView<>(MenuLinkView.from(link))).build();

        } catch (Exception e) {
            Logger.error(this, "Error unpublishing menu link " + identifier + ": " + e.getMessage(), e);
            return ResponseUtil.mapExceptionResponse(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a host by UUID identifier first, falling back to hostname lookup.
     */
    private Host resolveHost(final String siteId, final User user) throws Exception {
        Host site = APILocator.getHostAPI().find(siteId, user, false);
        if (site == null || !UtilMethods.isSet(site.getIdentifier())) {
            site = APILocator.getHostAPI().findByName(siteId, user, false);
        }
        if (site == null || !UtilMethods.isSet(site.getIdentifier())) {
            throw new IllegalArgumentException("Site not found: " + siteId);
        }
        return site;
    }

    /**
     * Resolves a folder by path on the given host.
     */
    private Folder resolveFolder(final String folderPath, final Host site, final User user) throws Exception {
        final Folder folder = APILocator.getFolderAPI()
                .findFolderByPath(folderPath, site, user, false);
        if (folder == null || !InodeUtils.isSet(folder.getInode())) {
            throw new IllegalArgumentException(
                    "Folder not found: " + folderPath + " on site " + site.getHostname());
        }
        return folder;
    }

    /**
     * Fetches all working menu links whose {@code identifier.parent_path} falls within
     * {@code maxDepth} levels below {@code rootPath} on the given site, using two SQL
     * round-trips regardless of how many folders are involved:
     *
     * <ol>
     *   <li>A {@link DotConnect} query resolves working link inodes from
     *       {@code link_version_info} joined to {@code identifier}, filtered by
     *       {@code host_inode} and a {@code parent_path LIKE} prefix. Depth is enforced
     *       in Java by counting path separators.</li>
     *   <li>A single Hibernate {@code IN} query batch-loads the {@link Link} objects.</li>
     * </ol>
     *
     * <p>Callers are expected to apply permission filtering on the returned list.
     */
    private List<Link> findLinksUnderPath(final String rootPath, final Host site,
                                           final int maxDepth) throws Exception {
        final String normalizedPath = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        final long baseSlashes = normalizedPath.chars().filter(c -> c == '/').count();

        final DotConnect dc = new DotConnect();
        dc.setSQL("SELECT vinfo.working_inode, id.parent_path "
                + "FROM link_version_info vinfo "
                + "JOIN identifier id ON id.id = vinfo.identifier "
                + "WHERE id.host_inode = ? "
                + "AND id.parent_path LIKE ?");
        dc.addParam(site.getIdentifier());
        dc.addParam(normalizedPath + "%");

        final List<String> inodes = dc.loadResults().stream()
                .filter(row -> {
                    final Object p = row.get("parent_path");
                    if (p == null) return false;
                    return p.toString().chars().filter(c -> c == '/').count()
                            <= baseSlashes + maxDepth;
                })
                .map(row -> {
                    final Object v = row.get("working_inode");
                    return v != null ? v.toString() : null;
                })
                .filter(inode -> inode != null)
                .collect(Collectors.toList());

        if (inodes.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        final List<Link> links = (List<Link>) HibernateUtil.getSession()
                .createQuery("FROM " + Link.class.getName() + " WHERE inode IN (:inodes)")
                .setParameterList("inodes", inodes)
                .list();

        return links != null ? links : java.util.Collections.emptyList();
    }

    /**
     * Sorts a list of links by one or more comma-separated field names. Each token is passed to
     * {@link #buildComparator} and chained via {@code thenComparing}. Falls back to title for
     * unrecognised tokens.
     */
    private List<Link> sortLinks(final List<Link> links, final String orderBy) {
        if (!UtilMethods.isSet(orderBy)) {
            return links;
        }
        final String[] fields = orderBy.split(",");
        java.util.Comparator<Link> comparator = buildComparator(fields[0].trim());
        for (int i = 1; i < fields.length; i++) {
            comparator = comparator.thenComparing(buildComparator(fields[i].trim()));
        }
        return links.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Returns a {@link java.util.Comparator} for a single sort field token. The token is
     * normalised to lowercase with underscores stripped before matching, so {@code sort_order},
     * {@code sortOrder}, and {@code sortorder} are all equivalent. Falls back to title for
     * unrecognised tokens. String comparisons are case-insensitive.
     */
    private java.util.Comparator<Link> buildComparator(final String field) {
        switch (field.trim().toLowerCase().replace("_", "")) {
            case "friendlyname":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getFriendlyName()) ? l.getFriendlyName().toLowerCase() : "");
            case "url":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getUrl()) ? l.getUrl().toLowerCase() : "");
            case "target":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getTarget()) ? l.getTarget().toLowerCase() : "");
            case "linktype":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getLinkType()) ? l.getLinkType().toLowerCase() : "");
            case "linkcode":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getLinkCode()) ? l.getLinkCode().toLowerCase() : "");
            case "sortorder":
                return java.util.Comparator.comparingInt(Link::getSortOrder);
            case "moddate":
                return java.util.Comparator.comparing(
                        (Link l) -> l.getModDate() != null ? l.getModDate() : new java.util.Date(0));
            case "moduser":
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getModUser()) ? l.getModUser().toLowerCase() : "");
            case "showonmenu":
                // false < true — links shown on menu sort last
                return java.util.Comparator.comparing(Link::isShowOnMenu);
            case "path":
                return java.util.Comparator.comparing((Link l) -> {
                    try {
                        final String p = APILocator.getIdentifierAPI()
                                .find(l.getIdentifier()).getParentPath();
                        return p != null ? p : "";
                    } catch (Exception e) {
                        Logger.warn(this, "Could not resolve path for link "
                                + l.getIdentifier() + ": " + e.getMessage());
                        return "";
                    }
                });
            case "title":
            default:
                return java.util.Comparator.comparing(
                        (Link l) -> UtilMethods.isSet(l.getTitle()) ? l.getTitle().toLowerCase() : "");
        }
    }

    /**
     * Populates a new {@link Link} from a {@link MenuLinkForm}, applying create-time defaults
     * for optional fields that were not supplied in the request.
     */
    private Link buildLink(final MenuLinkForm form) {
        final Link link = new Link();
        applyForm(form, link);
        // Apply defaults for fields not provided in the POST body.
        // applyForm skips null fields, so these only fire when the caller omitted them.
        if (form.getTarget() == null) {
            link.setTarget("_self");
        }
        if (form.getLinkType() == null) {
            link.setLinkType(Link.LinkType.EXTERNAL.toString());
        }
        if (form.isShowOnMenu() == null) {
            link.setShowOnMenu(false);
        }
        if (form.getSortOrder() == null) {
            link.setSortOrder(0);
        }
        if (form.getUrl() == null) {
            link.setUrl("");
        }
        if (form.getProtocol() == null) {
            link.setProtocal("");
        }
        if (form.getLinkCode() == null) {
            link.setLinkCode("");
        }
        if (form.getInternalLinkIdentifier() == null) {
            link.setInternalLinkIdentifier("");
        }
        if (form.getFriendlyName() == null) {
            link.setFriendlyName("");
        }
        return link;
    }

    /**
     * Validates that the companion field required by each linkType is present.
     */
    private void validateLinkTypeFields(final MenuLinkForm form) {
        final String type = UtilMethods.isSet(form.getLinkType())
                ? form.getLinkType().toUpperCase()
                : Link.LinkType.EXTERNAL.toString();

        if (Link.LinkType.EXTERNAL.toString().equals(type) && !UtilMethods.isSet(form.getUrl())) {
            throw new IllegalArgumentException("url is required for linkType EXTERNAL");
        }
        if (Link.LinkType.INTERNAL.toString().equals(type)
                && !UtilMethods.isSet(form.getInternalLinkIdentifier())) {
            throw new IllegalArgumentException(
                    "internalLinkIdentifier is required for linkType INTERNAL");
        }
        if (Link.LinkType.CODE.toString().equals(type) && !UtilMethods.isSet(form.getLinkCode())) {
            throw new IllegalArgumentException("linkCode is required for linkType CODE");
        }
    }

    /**
     * Applies all editable {@link MenuLinkForm} fields onto an existing {@link Link}.
     *
     * <p>Fields that are {@code null} in the form are left unchanged on the link —
     * this makes partial PUT requests safe. String fields set to {@code ""} are
     * applied as-is (empty string is a valid intentional value).
     *
     * <p>Always stamps {@code modDate} with the current time; a null modDate causes
     * the legacy DWR-based folder browser to crash with a JS exception.
     *
     * <p>Does not touch identifier, inode, or parent — those are managed by the API.
     */
    private void applyForm(final MenuLinkForm form, final Link link) {
        link.setModDate(new java.util.Date());

        if (form.getTitle() != null) {
            link.setTitle(form.getTitle());
        }
        if (form.getFriendlyName() != null) {
            link.setFriendlyName(form.getFriendlyName());
        }
        if (form.getUrl() != null) {
            link.setUrl(form.getUrl());
        }
        if (form.getProtocol() != null) {
            link.setProtocal(form.getProtocol());
        }
        if (form.getTarget() != null) {
            link.setTarget(form.getTarget());
        }
        if (form.getLinkType() != null) {
            link.setLinkType(form.getLinkType());
        }
        if (form.getLinkCode() != null) {
            link.setLinkCode(form.getLinkCode());
        }
        if (form.getInternalLinkIdentifier() != null) {
            link.setInternalLinkIdentifier(form.getInternalLinkIdentifier());
        }
        if (form.isShowOnMenu() != null) {
            link.setShowOnMenu(form.isShowOnMenu());
        }
        if (form.getSortOrder() != null) {
            link.setSortOrder(form.getSortOrder());
        }
    }
}
