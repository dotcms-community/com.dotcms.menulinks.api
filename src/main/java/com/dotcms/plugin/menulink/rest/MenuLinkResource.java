package com.dotcms.plugin.menulink.rest;

import com.dotcms.rest.InitDataObject;
import com.dotcms.rest.ResponseEntityView;
import com.dotcms.rest.WebResource;
import com.dotcms.rest.annotation.NoCache;
import com.dotcms.rest.api.v1.authentication.ResponseUtil;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
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
@Tag(name = "Menu Links", description = "CRUD operations for dotCMS Menu Link assets")
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
            @Parameter(description = "Parent folder path to filter by (e.g. /about)")
            @QueryParam("folderPath") final String folderPath,
            @Parameter(description = "Include archived links")
            @QueryParam("includeArchived") @DefaultValue("false") final boolean includeArchived,
            @Parameter(description = "Zero-based offset for pagination")
            @QueryParam("offset") @DefaultValue("0") final int offset,
            @Parameter(description = "Maximum number of results (0 = no limit)")
            @QueryParam("limit") @DefaultValue("50") final int limit,
            @Parameter(description = "Sort field")
            @QueryParam("orderBy") @DefaultValue("title") final String orderBy) {

        final InitDataObject auth = webResource.init(request, response, true);
        final User user = auth.getUser();

        try {
            String resolvedHostId = null;
            String parentInode    = null;

            if (UtilMethods.isSet(siteId)) {
                final Host site = resolveHost(siteId, user);
                resolvedHostId = site.getIdentifier();
            }

            if (UtilMethods.isSet(folderPath) && UtilMethods.isSet(resolvedHostId)) {
                final Host site = APILocator.getHostAPI().find(resolvedHostId, user, false);
                final Folder folder = APILocator.getFolderAPI()
                        .findFolderByPath(folderPath, site, user, false);
                if (folder != null && InodeUtils.isSet(folder.getInode())) {
                    parentInode = folder.getInode();
                }
            }

            final List<Link> links = APILocator.getMenuLinkAPI().findLinks(
                    user,
                    includeArchived,
                    null,            // no extra params map
                    resolvedHostId,
                    null,            // inode filter
                    null,            // identifier filter
                    parentInode,
                    offset,
                    limit,
                    orderBy);

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

            final Host site = resolveHost(form.getSiteId(), user);
            final Folder folder = resolveFolder(form.getFolderPath(), site, user);

            final Link link = buildLink(form);
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

            APILocator.getVersionableAPI().setWorking(link);

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
     * Populates a new {@link Link} from a {@link MenuLinkForm}.
     */
    private Link buildLink(final MenuLinkForm form) {
        final Link link = new Link();
        applyForm(form, link);
        return link;
    }

    /**
     * Applies all editable {@link MenuLinkForm} fields onto an existing {@link Link}.
     * Does not touch identifier, inode, or parent — those are managed by the API.
     */
    private void applyForm(final MenuLinkForm form, final Link link) {
        if (UtilMethods.isSet(form.getTitle())) {
            link.setTitle(form.getTitle());
        }
        link.setFriendlyName(UtilMethods.isSet(form.getFriendlyName())
                ? form.getFriendlyName() : form.getTitle());
        link.setUrl(UtilMethods.isSet(form.getUrl())     ? form.getUrl()     : "");
        link.setProtocal(UtilMethods.isSet(form.getProtocol()) ? form.getProtocol() : "");
        link.setTarget(UtilMethods.isSet(form.getTarget()) ? form.getTarget() : "_self");
        link.setLinkType(UtilMethods.isSet(form.getLinkType())
                ? form.getLinkType() : Link.LinkType.EXTERNAL.toString());
        link.setLinkCode(UtilMethods.isSet(form.getLinkCode()) ? form.getLinkCode() : "");
        link.setInternalLinkIdentifier(
                UtilMethods.isSet(form.getInternalLinkIdentifier())
                        ? form.getInternalLinkIdentifier() : "");
        link.setShowOnMenu(form.isShowOnMenu());
        link.setSortOrder(form.getSortOrder());
    }
}
