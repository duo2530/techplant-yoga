# TechPlant Yoga

[简体中文](./README.md) | English

A yoga & pilates booking and operations management project, consisting of an admin web console, a user-facing mini program, a Java backend, and the project design documents.

The project focuses on an MVP built from verifiable business vertical slices. Store, classroom, coach, course, schedule, member, membership card, and reservation modules are in place.

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

User-facing pages depend only on the `api` layer: stores, schedules, coaches, reservations, account sign-in, and the membership card list already call the real backend (`frontend/uni-app/api/store.js`, `schedule.js`, `coach.js`, `booking.js`, `auth.js`, and `GET /api/member-cards` in `member.js`), while courses, the home page, activities, messages, trial classes, and the rest of the member center still use mock data. When connecting the real backend, replace the corresponding files under `frontend/uni-app/api` the same way.

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
│  ├─ 需求分析/          # Requirements analysis, use-case index and 9 use cases
│  ├─ 需求文档/          # Global overview, feature inventory, process flows, state transition diagrams
│  │  ├─ 管理端/         # Admin feature list, page structure diagram, admin prototype
│  │  └─ 用户端/         # User-side feature list, structure diagrams, hi-fi prototype
│  ├─ 产品分析/          # Real-app screenshots, page/object inventory, navigation analysis
│  ├─ 概要设计/          # High-level design
│  ├─ 详细设计/          # Per-module detailed designs
│  ├─ 编码开发/          # Project infrastructure, AI agent development handbook
│  └─ 模板/              # Document templates
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

**Start MySQL and Redis with Docker (optional)**: `docker-compose.yml` at the repository root provides MySQL 8.0 (`root` / `123456`, database `yoga`) and Redis 7, matching the defaults in `application*.yml`. On first start it imports the three SQL scripts below in order, so you can skip step 1:

```powershell
docker compose up -d --wait   # start the dependencies
start.bat                     # build and start the backend on 8080, admin console on 80, user app H5 on 9090
docker compose down -v        # stop and delete the data volumes; the next start re-initializes
```

### 1. Import the Three SQL Scripts into the Database

The backend depends on three bootstrap scripts, all located in `backend/sql`. They **must be executed in order**:

| Order | Script | Purpose |
| --- | --- | --- |
| 1 | `backend/sql/ry_20260417.sql` | RuoYi base tables and seed data (`sys_user` including the `admin` account, `sys_menu`, dictionaries, etc.) |
| 2 | `backend/sql/quartz.sql` | Quartz scheduled task tables (`QRTZ_*`) |
| 3 | `backend/sql/yoga.sql` | 10 yoga business tables (`t_store`, `t_store_no_counter`, `t_classroom`, `t_coach`, `t_course`, `t_schedule`, `t_member`, `t_member_card`, `t_reservation`, `t_member_card_transaction`), business menus (2001–2008), the `store_region` region and `member_level` member level dictionaries, a scheduled job, and sample data |

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
SELECT menu_name FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2008;   -- should return "经营基础" and 8 business menus
SELECT COUNT(*) FROM t_store;                                   -- the 10 business tables exist
```

`yoga.sql` is re-runnable: business tables are dropped with `DROP TABLE IF EXISTS` and recreated, and menus clean up IDs 2000–2010 and rewrite 2000–2008 without touching RuoYi's built-in "System Management" menus. Note that **recreating the business tables wipes business data**.

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

**One-click start and stop on Windows (after the environment, database, and configuration steps above)**

Double-click `start.bat` at the repository root to start the backend (8080), the admin console (80), and the user app H5 (9090) together. Double-click `stop.bat` to shut these services and their child processes down.

- Start first runs a Maven package (tests skipped), and runs `npm ci` for a frontend that has no `node_modules`. The first start needs network access to download dependencies.
- MySQL and Redis must already be running. The scripts do not initialize the database and do not launch WeChat DevTools.
- The services run in the background and keep running after the start window closes. Logs and process records live in `.tmp/dev/`. If start fails, the services started in that run are shut down and the logs are kept.
- A repeated start skips services the script already started. A port held by another program is reported as an error. Stop only handles processes the script recorded, not services started from an IDE or another terminal.
- After changing backend code, stop and start again to repackage. You can also run `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\dev.ps1 -Action Start` in PowerShell (replace `Start` with `Stop` to shut down).

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
- After signing in, the "经营基础" (Business Foundation) menu group should contain store, classroom, coach, course, schedule, member, membership card, and reservation management. If the group is empty, the menu data in `yoga.sql` was not imported successfully, or you need to sign in again.
- You can open the login page with the backend stopped, but signing in will fail, so complete step 3 first.

### 5. Run the User Mini Program

`frontend/uni-app` can be started and built from the npm command line, and needs Node.js >= 20.19.

```powershell
cd frontend/uni-app
npm ci
npm run dev:h5
# WeChat Mini Program development: npm run dev:mp-weixin
# Production build: npm run build:h5 or npm run build:mp-weixin
```

The H5 dev server runs at `http://localhost:9090` by default. Import `dist/dev/mp-weixin` into WeChat DevTools to debug; production output goes to `dist/build/h5` and `dist/build/mp-weixin`. See the [user app README](frontend/uni-app/README.md) for details.

You can also keep using HBuilderX:

1. **Open the project in HBuilderX**: HBuilderX → File → Open Directory → select the `frontend/uni-app` directory.
2. **Set the AppID**: double-click `manifest.json` in the project and choose "WeChat Mini Program Configuration" (微信小程序配置) in the visual editor on the left, then fill in your own WeChat Mini Program AppID. You can also edit `mp-weixin.appid` in `manifest.json` directly (it currently holds the placeholder `touristappid`, which **must be replaced**, otherwise WeChat DevTools reports an invalid AppID).
   - The AppID is available in the WeChat Official Accounts Platform under "Development → Development Management → Development Settings".
   - The `.env` file at the repository root (ignored by `.gitignore`, not committed) records the local `APP_ID` and can be used as a reference; the project code does not read that file.
3. **Prepare WeChat DevTools**: in HBuilderX, set "Tools → Settings → Run Configuration → WeChat DevTools path" to your local installation, and make sure the service port is enabled in WeChat DevTools under "Settings → Security Settings". Otherwise HBuilderX cannot push the build output to it.
4. **Run to the mini program**: back in HBuilderX, click **"Run" (运行) in the top-left corner → "Run to Mini Program Simulator" (运行到小程序模拟器) → "WeChat DevTools"**, then wait for the first build to finish (it is slower the first time). WeChat DevTools opens automatically and loads the mini program.
5. During development, tick "Do not verify valid domains" (不校验合法域名) under "Details → Local Settings" in WeChat DevTools.
6. The user app is currently split: stores, schedules, coaches, reservations, account sign-in, and the membership card list use real APIs (`/api/stores`, `/api/schedules`, `/api/coaches`, `/api/reservations`, `/api/auth`, `/api/member-cards`, with the address taken from `baseUrl` in `frontend/uni-app/config.js`, default `http://localhost:8080`), while the remaining domains still use mock data. To see real data on the home, booking, and my-reservations pages, complete step 3 and start the backend first; without it, those pages only show empty states.
7. To just look at the pages quickly, choose "Run → Run to Browser" instead; the H5 port is `9090` by default.

> After changing `manifest.json` or `pages.json`, recompile; in some cases WeChat DevTools must be restarted.

### 6. Verification Checklist

| Check | Expected result |
| --- | --- |
| Database | The `yoga` database contains the `sys_*` and `QRTZ_*` tables plus the 10 business tables such as `t_store` |
| Backend | The log shows a successful RuoYi start, and `http://localhost:8080` is reachable |
| Admin console | `npm install` and `npm run dev` finish without errors, and `http://localhost` opens the login page |
| Sign-in | `admin / admin123` signs in successfully and shows the 8 business menus under "经营基础" |
| Business pages | Store / classroom / coach / course / schedule / member / membership card / reservation pages can page through records, and create and edit them where the page supports it |
| User app | `npm run dev:h5`, `npm run build:mp-weixin`, or HBuilderX compiles without errors and WeChat DevTools shows the 4 tabBar pages |

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

Backend module build:

```powershell
cd backend
mvn -pl ruoyi-yoga -am -DskipTests clean compile
```

Test strategy, commands, and coverage boundaries: [AI Agent testing guide](docs/系统测试/AI%20Agent测试手册.md).

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

### Documentation Conventions (drift prevention)

Each kind of content has exactly one source of truth; other documents reference it rather than restating it:

| Content | Single source of truth | How other documents refer to it |
| --- | --- | --- |
| Feature scope and phasing | `docs/需求文档/功能清单汇总.md` (phased by the "版本" column: v1.0 phase 1, v2.0 phase 2, v3.0 phase 3) | Cite the module and feature name; never copy the feature list |
| User-facing screens and navigation | `docs/产品分析/用户端页面与跳转梳理.md` (P/T/C IDs) | Cite IDs only |
| User-facing API contract | `docs/详细设计/用户端接口详细设计.md` | Module designs cover admin APIs only; user-facing APIs are defined once, there |
| Global technical conventions | `backend/AGENTS.md`, `frontend/uni-app/AGENTS.md` | Reference them; never start a parallel set |

Three hard requirements:

1. **Bump version and date together** — changing the body requires updating the header version/date and adding a changelog row; the header version must equal the highest version in the changelog.
2. **Reference current versions** — always cite the *current* version and a correct relative path; re-check every relative link after moving files.
3. **Append, don't rewrite history** — changelogs and past decisions are added to, never edited; a superseded rule is explained in the changelog instead of being deleted.

## License

This project is licensed under the [MIT License](./LICENSE).
