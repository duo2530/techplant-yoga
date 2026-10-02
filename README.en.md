# TechPlant Yoga

[简体中文](./README.md) | [English](./README.en.md)

A yoga & pilates booking and operations management project, consisting of an admin web console, a user-facing mini program, a Java backend, and the project design documents.

The project focuses on an MVP built from verifiable business vertical slices. Store, coach, and course modules are already in place, and scheduling and booking capabilities are being added incrementally.

## Repository Layout

| Directory | Description |
| --- | --- |
| `backend` | RuoYi-based Java backend with system management and yoga business modules |
| `frontend/pc` | Operations admin web console for stores, courses, coaches, and other back-office management |
| `frontend/uni-app` | User-facing uni-app project targeting WeChat Mini Program and H5 |
| `docs` | Requirements analysis, high-level design, detailed design, prototypes, and business object material |

## Tech Stack

### Backend

- Java 8
- Spring Boot 2.5.15
- RuoYi 3.9.2
- Spring MVC, Spring Security
- MyBatis-Plus 3.5.5
- MySQL, Druid, Redis (provided through the RuoYi runtime configuration)
- Springfox Swagger
- Maven multi-module build

Yoga business code lives under the `com.techplant.yoga` package. Business modules are layered as DO, DTO, VO, DAO, Service, and Controller. APIs never return persistence entities directly, and paginated endpoints keep the RuoYi `TableDataInfo` response structure.

### Admin Console

- Vue 3.5
- Vite 6
- Element Plus 2.13
- Pinia 3
- Axios
- Vue Router 4

The console is based on the RuoYi Vue3 scaffold. Business pages live in `frontend/pc/src/views` and API wrappers in `frontend/pc/src/api`.

### User Mini Program

- uni-app
- Vue 3
- Pinia
- WeChat Mini Program
- HBuilderX

User-facing pages depend only on the `api` layer: stores and schedules already call the real backend (`frontend/uni-app/api/store.js`, `api/schedule.js` → `GET /api/stores`, `GET /api/schedules`), while the remaining business domains still use mock data. When connecting the real backend, replace the corresponding files under `frontend/uni-app/api` the same way.

## Project Structure

```text
techplant-yoga/
├─ backend/
│  ├─ ruoyi-admin/       # Backend bootstrap module
│  ├─ ruoyi-common/      # Shared utilities and base capabilities
│  ├─ ruoyi-framework/   # RuoYi framework capabilities
│  ├─ ruoyi-system/      # System management module
│  ├─ ruoyi-yoga/        # Yoga business module
│  ├─ ruoyi-quartz/      # Scheduled task module
│  ├─ ruoyi-generator/   # Code generator module
│  └─ sql/               # Base tables and yoga business bootstrap scripts
├─ frontend/
│  ├─ pc/
│  │  └─ src/
│  │     ├─ api/         # Admin console APIs
│  │     ├─ views/       # Admin console pages
│  │     ├─ router/      # Routes and dynamic menus
│  │     └─ store/       # Pinia stores
│  └─ uni-app/
│     ├─ api/            # User-facing API layer
│     ├─ mock/           # User-facing mock data
│     ├─ pages/          # Mini program pages
│     ├─ components/     # Business components
│     └─ static/         # Images, fonts, and style assets
├─ docs/
│  ├─ 需求分析/           # Requirements analysis
│  ├─ 概要设计/           # High-level design
│  ├─ 详细设计/           # Detailed design
│  ├─ 原型/               # Prototypes
│  └─ 需求文档/           # Requirements documents
├─ .gitignore
├─ LICENSE
├─ README.md
└─ README.en.md
```

## Quick Start

Follow the steps below in order to get the full "database → backend → admin console → user mini program" chain running locally (step 0 covers prerequisites; steps 1–6 are the actual operations).

| Convention | Default value |
| --- | --- |
| Database name | `yoga` |
| Backend URL | `http://localhost:8080` |
| Admin console URL | `http://localhost` (Vite dev port 80) |
| Admin console credentials | `admin / admin123` |
| User app H5 port | `9090` (only when running in a browser) |

### 0. Prerequisites

| Dependency | Version / notes                                                                        |
| --- |----------------------------------------------------------------------------------------|
| JDK | 8                                                                                      |
| Maven | 3.3.9 or later                                                                         |
| MySQL | 5.7 / 8.0, `utf8mb4` character set recommended                                         |
| Redis | 5.0 or later, default `localhost:6379` with no password                                |
| Node.js | 18 or later (the admin console uses Vite 6)                                            |
| HBuilderX | Latest stable release, used to compile the user mini program                           |
| WeChat DevTools | Latest stable release, with the service port enabled in "Settings → Security Settings" |

### 1. Import the Three SQL Scripts into the Database

The backend depends on three bootstrap scripts, all located in `backend/sql`. They **must be executed in order**:

| Order | Script | Purpose |
| --- | --- | --- |
| 1 | `backend/sql/ry_20260417.sql` | RuoYi base tables and seed data (`sys_user` including the `admin` account, `sys_menu`, dictionaries, etc.) |
| 2 | `backend/sql/quartz.sql` | Quartz scheduled task tables (`QRTZ_*`) |
| 3 | `backend/sql/yoga.sql` | Yoga business tables (`t_store`, `t_classroom`, `t_coach`, `t_course`, `t_schedule`), business menus (2000–2005), the `store_region` dictionary, and sample data |

`ry_20260417.sql` and `quartz.sql` contain **no `CREATE DATABASE` or `use` statement**, so create the database yourself first. The default database name is `yoga`, matching `application-druid.yml`:

```sql
CREATE DATABASE IF NOT EXISTS `yoga` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**Option A: GUI tool (recommended)**

Connect to MySQL with Navicat / DBeaver / MySQL Workbench → create the `yoga` database → open and execute the three `.sql` files in the order above (do not reverse the order; `yoga.sql` must run on a database that already contains the RuoYi base tables).

**Option B: mysql command line**

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS yoga DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p --default-character-set=utf8mb4 yoga
```

`cd` to the repository root first, then run the three scripts with `source` inside the `mysql>` prompt:

```sql
source backend/sql/ry_20260417.sql;
source backend/sql/quartz.sql;
source backend/sql/yoga.sql;
```

> PowerShell does not support `<` input redirection, so the `source` command inside the mysql client is used here to avoid encoding and syntax issues.

**Post-import sanity check**:

```sql
USE yoga;
SELECT COUNT(*) FROM sys_user;                                  -- should contain at least the admin account
SELECT menu_name FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2005;
SELECT COUNT(*) FROM t_store;                                   -- the 5 business tables exist
```

`yoga.sql` is re-runnable: business tables are dropped with `DROP TABLE IF EXISTS` and recreated, and menus only clean up and rewrite IDs 2000–2006 without touching RuoYi's built-in "System Management" menus. Note that **recreating the business tables wipes business data**.

### 2. Update the Backend Configuration

| Configuration file | What to change |
| --- | --- |
| `backend/ruoyi-admin/src/main/resources/application-druid.yml` | `spring.datasource.druid.master.url` (default `jdbc:mysql://localhost:3306/yoga`), `username` (default `root`), `password` (default `123456`) |
| `backend/ruoyi-admin/src/main/resources/application.yml` | `spring.redis.host / port / password` (default `localhost:6379`, empty password), `server.port` (default `8080`), and the `ruoyi.profile` upload directory (default `D:/ruoyi/uploadPath`; make sure that directory exists on Windows) |

A few notes:

- If you did not name the database `yoga`, update the database name in `master.url` as well.
- On MySQL 8.0 without an SSL certificate, change `useSSL=true` in the URL to `useSSL=false&allowPublicKeyRetrieval=true`, otherwise the connection may fail.
- `token.secret`, the `/druid/*` console credentials (default `ruoyi/123456`), and `ruoyi.profile` are all scaffold defaults intended for local development. They must be changed before going to production.

### 3. Build and Start the Backend

**Option A: start from an IDE**

Open `backend` in IntelliJ IDEA / Eclipse (the JDK must be 8) and run the bootstrap class `backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`.

**Option B: start from the command line**

```powershell
cd backend
mvn -DskipTests clean package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

On Windows you can also use the provided scripts `backend/bin/package.bat` (build) and `backend/bin/run.bat` (run); on Linux / macOS use `backend/ry.sh start`.

Signs of a successful start:

- The console logs something like "若依启动成功" (RuoYi started successfully) with no port conflict on `8080`;
- `http://localhost:8080` responds in a browser;
- Optional: API docs at `http://localhost:8080/swagger-ui/index.html` and the Druid console at `http://localhost:8080/druid`.

Common causes of a failed start: MySQL or Redis is not running, the database name / user / password in the configuration is wrong, or the three SQL scripts from step 1 were not imported completely.

### 4. Start the Admin Console (PC)

```powershell
cd frontend/pc
npm install
npm run dev
```

- Run `npm install` to install dependencies first, then `npm run dev` to start. Vite builds the app and opens the browser automatically.
- The dev port is **80**, so the URL is `http://localhost`. If the port is occupied or requires privileges, temporarily change `server.port` in `frontend/pc/vite.config.js`.
- In development, API calls are proxied from `/dev-api` to `http://localhost:8080`. To point at a different backend, change `baseUrl` at the top of `frontend/pc/vite.config.js`.
- On the login page, enter the username **`admin`** and password **`admin123`** (the captcha is a math question — enter the computed result).
- After signing in, the "经营基础" (Business Foundation) menu group should contain store, classroom, coach, course, and schedule management. If the group is empty, the menu data in `yoga.sql` was not imported successfully, or you need to sign in again.
- You can open the login page with the backend stopped, but signing in will fail, so complete step 3 first.

### 5. Run the User Mini Program

`frontend/uni-app` is an HBuilderX project. It **has no `package.json`, so it cannot and need not be started with npm**.

1. **Open the project in HBuilderX**: HBuilderX → File → Open Directory → select the `frontend/uni-app` directory.
2. **Set the AppID**: double-click `manifest.json` in the project and choose "WeChat Mini Program Configuration" (微信小程序配置) in the visual editor on the left, then fill in your own WeChat Mini Program AppID. You can also edit `mp-weixin.appid` in `manifest.json` directly (it currently holds the placeholder `touristappid`, which **must be replaced**, otherwise WeChat DevTools reports an invalid AppID).
   - The AppID is available in the WeChat Official Accounts Platform under "Development → Development Management → Development Settings".
   - The `.env` file at the repository root (ignored by `.gitignore`, not committed) records the local `APP_ID` and can be used as a reference; the project code does not read that file.
3. **Prepare WeChat DevTools**: in HBuilderX, set "Tools → Settings → Run Configuration → WeChat DevTools path" to your local installation, and make sure the service port is enabled in WeChat DevTools under "Settings → Security Settings". Otherwise HBuilderX cannot push the build output to it.
4. **Run to the mini program**: back in HBuilderX, click **"Run" (运行) in the top-left corner → "Run to Mini Program Simulator" (运行到小程序模拟器) → "WeChat DevTools"**, then wait for the first build to finish (it is slower the first time). WeChat DevTools opens automatically and loads the mini program.
5. During development, tick "Do not verify valid domains" (不校验合法域名) under "Details → Local Settings" in WeChat DevTools.
6. The user app is currently split: stores and schedules use real APIs (`/api/stores`, `/api/schedules`, with the address taken from `baseUrl` in `frontend/uni-app/config.js`, default `http://localhost:8080`), while the remaining domains still use mock data. To see real data on the home and booking pages, complete step 3 and start the backend first; without it, those pages only show empty states.
7. To just look at the pages quickly, choose "Run → Run to Browser" instead; the H5 port is `9090` by default.

> After changing `manifest.json` or `pages.json`, recompile; in some cases WeChat DevTools must be restarted.

### 6. Verification Checklist

| Check | Expected result |
| --- | --- |
| Database | The `yoga` database contains the `sys_*` and `QRTZ_*` tables plus the 5 business tables such as `t_store` |
| Backend | The log shows a successful RuoYi start, and `http://localhost:8080` is reachable |
| Admin console | `npm install` and `npm run dev` finish without errors, and `http://localhost` opens the login page |
| Sign-in | `admin / admin123` signs in successfully and shows the 5 business menus under "经营基础" |
| Business pages | Store / classroom / coach / course / schedule pages can page through, create, and edit records |
| User app | HBuilderX compiles without errors and WeChat DevTools shows the 4 tabBar pages |

### Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Backend fails to connect to Redis | Redis is not running, or the `spring.redis` host/password in `application.yml` is wrong |
| Backend fails to connect to the database | MySQL is not running, the database name / user / password is wrong, or MySQL 8 hits the SSL parameter issue (see step 2) |
| Admin console login returns an API error / 404 | The backend is not running, or it is not on `8080` (if you changed the port, update `baseUrl` in `vite.config.js` too) |
| No business menus after signing in | `backend/sql/yoga.sql` did not execute successfully, or you did not sign in again afterwards |
| `npm run dev` fails to start | Node.js is too old (needs 18+), or port 80 is occupied (change `server.port`) |
| WeChat DevTools reports an invalid AppID | The AppID in step 5 is still the `touristappid` placeholder — replace it with your own AppID |
| Mini program API requests fail | Tick "Do not verify valid domains" during development and make sure the backend is running |

## Current Store Module

Store management currently provides:

- Paged store listing and store detail queries in the admin console
- Creating a store
- Updating a store (full edit, returning the latest detail)
- Deleting a store (physical delete; the service first verifies that the store has no unfinished schedules)
- Anonymous, paged store listing for the user app, with filtering by region and keyword search
- Store type: flagship store, boutique store
- Region: stored as an administrative division code, with the district name resolved through the `store_region` dictionary
- Contact phone, address, and a single store image
- A single free-text business hours field

In phase one, stores have **no enabled/disabled status** and no logical deletion: deleting a store executes `DELETE` on the row.

Admin console entry:

```text
经营基础 (Business Foundation) -> 门店管理 (Store Management)
```

Main endpoints:

```text
GET    /admin/stores
GET    /admin/stores/{storeId}
POST   /admin/stores
PUT    /admin/stores/{storeId}
DELETE /admin/stores/{storeId}
GET    /api/stores
```

## Verification Commands

Backend module build and targeted store tests:

```powershell
cd backend
mvn -pl ruoyi-yoga -am -DskipTests clean compile
mvn -pl ruoyi-yoga -am -Dtest=com.techplant.yoga.store.convert.StoreConverterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Admin console production build:

```powershell
cd frontend/pc
npm run build:prod
```

## Documentation Conventions

- Requirements, high-level design, and detailed design documents are written in Simplified Chinese.
- Unconfirmed business rules stay marked as `TODO-待确认` instead of expanding the domain prematurely.
- APIs use explicit DTOs and VOs and never expose persistence entities directly.
- Database changes are maintained in the business scripts under `backend/sql`.

## License

This project is licensed under the [MIT License](./LICENSE).
