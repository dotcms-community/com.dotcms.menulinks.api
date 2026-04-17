# dotCMS Menu Link REST API Plugin

An OSGi plugin that adds full CRUD REST API support for dotCMS Menu Links — the `Link` web-asset type that drives site navigation but has no native REST interface.

## Background

In dotCMS, most content is managed through the Workflow/Content API. Menu Links are a legacy exception: they are stored as a separate `WebAsset` type (not a content type), have their own database table (`links`), and are only manageable through the UI. This plugin surfaces the existing internal `MenuLinkAPI` over HTTP so that Menu Links can be created, updated, published, and deleted programmatically.

All endpoints surface with full interactivity and documentation in the API Playground under **Menu Links**.

## Endpoints

Base path: `/api/v1/menulinks`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/menulinks` | List menu links (filterable) |
| `GET` | `/api/v1/menulinks/{identifier}` | Get a single link by identifier |
| `POST` | `/api/v1/menulinks` | Create a new link |
| `PUT` | `/api/v1/menulinks/{identifier}` | Update an existing link |
| `DELETE` | `/api/v1/menulinks/{identifier}` | Delete all versions of a link |
| `PUT` | `/api/v1/menulinks/{identifier}/_publish` | Make the link live |
| `PUT` | `/api/v1/menulinks/{identifier}/_unpublish` | Remove the link from live |

### Query parameters — `GET /api/v1/menulinks`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `siteId` | string | — | Host UUID or hostname to filter by |
| `folderPath` | string | — | Parent folder path, e.g. `/about` |
| `includeArchived` | boolean | `false` | Include archived links |
| `offset` | int | `0` | Pagination offset |
| `limit` | int | `50` | Page size. Pass `0` to return all results. |
| `orderBy` | string | `title` | Sort field(s) — see valid values below |
| `depth` | int | `0` | Folder traversal depth when `folderPath` is set. `0` = the specified folder only, `1` = include direct child folders, `2` = grandchildren, etc. Large values may be expensive on deep trees. |

#### Valid `orderBy` values

Pass a single value or a **comma-separated list** for multi-level sorting:

```
?orderBy=path,title
?orderBy=path,sort_order
?orderBy=mod_date
```

Case and underscores are ignored, so `sort_order`, `sortOrder`, and `sortorder` are all equivalent.

| Value | Sorts by | Notes |
|-------|----------|-------|
| `title` *(default)* | Link title | |
| `friendly_name` | Friendly name | |
| `url` | Target URL | |
| `target` | Link target (`_self`, `_blank`, etc.) | |
| `link_type` | Link type (`EXTERNAL`, `INTERNAL`, `CODE`) | |
| `link_code` | Embedded code (for `CODE` links) | |
| `sort_order` | Integer sort position within the folder | |
| `mod_date` | Last modified date | |
| `mod_user` | User who last modified | |
| `show_on_menu` | Whether the link appears in navigation | |
| `path` | Parent folder path | In-memory only; only meaningful with `folderPath` + `depth > 0`. Sorts alphabetically, so `/a/` groups before `/a/x/` before `/b/`. |

> **Note:** when `folderPath` is specified, sorting is applied in-memory after fetching and all comma-separated tokens apply. Otherwise, only the first token is used and is passed directly to the database as a column name — invalid values are silently ignored by dotCMS, which falls back to `mod_date desc`.

### Request body — `POST` / `PUT`

```json
{
  "title": "Our Blog",
  "friendlyName": "Blog",
  "url": "https://example.com/blog",
  "protocol": "https://",
  "target": "_blank",
  "linkType": "EXTERNAL",
  "showOnMenu": true,
  "sortOrder": 2,
  "siteId": "demo.dotcms.com",
  "folderPath": "/",
  "publish": true
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `title` | Yes (create) | Display title |
| `friendlyName` | No | Falls back to `title` if omitted |
| `url` | For `EXTERNAL` links | Target URL |
| `protocol` | No | e.g. `https://` — prepended to `url` if the URL has no scheme |
| `target` | No | Link target attribute. Defaults to `_self` |
| `linkType` | No | `EXTERNAL` (default), `INTERNAL`, or `CODE` |
| `linkCode` | For `CODE` links | Raw code to embed |
| `internalLinkIdentifier` | For `INTERNAL` links | Identifier of the target asset |
| `showOnMenu` | No | Whether the link appears in navigation. Defaults to `false` |
| `sortOrder` | No | Integer sort position within the folder |
| `siteId` | Yes (create) | Host UUID or hostname |
| `folderPath` | Yes (create) | Folder path on the site. Supply on update to move the link |
| `publish` | No | If `true`, publishes the link immediately after saving |

### Response shape

All endpoints wrap their payload in dotCMS's standard `ResponseEntityView`:

```json
{
  "entity": {
    "inode": "abc123",
    "identifier": "def456",
    "title": "Our Blog",
    "friendlyName": "Blog",
    "url": "https://example.com/blog",
    "protocol": "https://",
    "target": "_blank",
    "linkType": "EXTERNAL",
    "linkCode": "",
    "internalLinkIdentifier": "",
    "showOnMenu": true,
    "sortOrder": 2,
    "live": true,
    "working": true,
    "siteId": "48190c8c-42c4-46af-8d1a-0cd5db894797",
    "folderPath": "/"
  },
  "errors": [],
  "messages": [],
  "i18nMessagesMap": {},
  "permissions": []
}
```

## Building

The plugin is fully standalone — no dotCMS source checkout required. `dotcms-core` is downloaded automatically from the dotCMS Artifactory repository.

```bash
./mvnw package
```

The output JAR is at `target/com.dotcms.plugin.menu-link-api-*.jar`.

To target a different dotCMS version, change the `dotcms-core.version` property in `pom.xml`:

```xml
<dotcms-core.version>26.04.06-01</dotcms-core.version>
```

Release tags follow the pattern `YY.MM.DD-NN` and can be browsed at
`https://artifactory.dotcms.cloud/artifactory/libs-release/com/dotcms/dotcms-core/`.

## Deployment

Drop the JAR into dotCMS's `felix/load/` directory. The OSGi container will start the bundle automatically and the endpoints will be live within seconds. To undeploy, remove the JAR — the `Activator` cleanly unregisters the REST resource on bundle stop.

Alternatively, use the dotCMS plugin manager UI at **Admin → Plugins**.

## Project structure

```
menu-link-api-plugin/
├── pom.xml
└── src/main/java/com/dotcms/plugin/menulink/
    ├── Activator.java              # OSGi BundleActivator
    └── rest/
        ├── MenuLinkResource.java  # JAX-RS endpoints
        ├── MenuLinkForm.java      # Request DTO
        └── MenuLinkView.java      # Response DTO
```

---

## FAQ

**Does this plugin bypass any dotCMS security or permission checks?**

No. Every endpoint authenticates via `WebResource.init()` (the same mechanism used by all dotCMS REST resources) and then delegates to `MenuLinkAPIImpl`, which performs its own `PermissionAPI` checks before touching any data. If the authenticated user lacks `WRITE` permission on the link or its parent folder, the API throws `DotSecurityException` and the request is rejected.

**Does this write to the database in a new or untested way?**

No. The call chain is `MenuLinkResource → MenuLinkAPIImpl → MenuLinkFactoryImpl → HibernateUtil/DotConnect` — identical to the chain the UI uses. The plugin adds only an HTTP layer on top of the existing internal API. The same transaction wrapper (`@WrapInTransaction`), the same cache invalidation (`NavToolCache`, `RefreshMenus`), and the same Hibernate calls are used regardless of whether the request originated from the UI or this API.

**Why aren't Menu Links a content type? Can I use the Workflow API instead?**

Menu Links predate dotCMS's content-type architecture and were never migrated into it. The Workflow API only operates on `Contentlet` objects, so it cannot create or manage Menu Links. This plugin is currently the only non-UI path to do so short of writing an OSGi plugin yourself.

**What's the difference between save and publish?**

A save creates or updates the *working* version of the link. It exists in the database but has no `live_inode` in `link_version_info`, so `NavTool` will not include it when rendering a live page. Calling `_publish` (or setting `"publish": true` in the request body) sets `live_inode`, making the link visible on the live site. This mirrors the two-step Save / Publish flow in the UI.

**Does updating a live link immediately affect the live site?**

Yes. Unlike contentlets, Menu Links do not maintain a separate working inode from the live inode. The factory's update path mutates the existing inode in place (`HibernateUtil.saveOrUpdate`). If the link is already live, the change is visible to live-site visitors as soon as the transaction commits. There is no staging buffer.

**Do publish/unpublish go through push publishing?**

The `_publish` endpoint calls `VersionableAPI.setLive()` directly and the `_unpublish` endpoint calls `VersionableAPI.removeLive()` directly. Both bypass the UI's `PublishFactory.publishAsset()` / `PublishFactory.unpublishAsset()` path, which additionally triggers push-publishing hooks if you have remote endpoints configured. If push publishing matters in your environment, test accordingly or extend those endpoints to call `PublishFactory` instead.

**What is the `protocol` field for?**

dotCMS's `Link` model stores a `protocol` prefix separately from the `url`. When rendering, it prepends `protocol` to `url` only if the URL doesn't already contain a scheme (`http://`, `https://`, `mailto:`, etc.). For most external links you can leave `protocol` empty and put the full URL in `url`. The field name is spelled correctly here; the underlying model field is misspelled as `protocal` (a legacy typo in the dotCMS codebase).
