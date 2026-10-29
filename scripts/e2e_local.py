# LOCAL ONLY. Run against a fresh local backend started with
#   SEED_ADMIN_EMAIL=admin@accuratepowertech.local SEED_ADMIN_PASSWORD=ChangeMe@123 SEED_ADMIN_NAME="Rahul Admin"
# then: python3 scripts/e2e_local.py   (creates roles, users, imports, quotations; checks rights, visibility and log filters)
# Never point it at a real server: it creates demo users and data.
"""End-to-end check of Accurate Powertech against a LOCAL server: creates real data, verifies rules and filters."""
import json, urllib.request, urllib.parse, uuid, io, struct, zlib, datetime, csv, sys

BASE = "http://localhost:8080/api"
results = []

def call(method, path, token=None, body=None, params=None, files=None, raw=False):
    url = BASE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    headers = {}
    data = None
    if token: headers["Authorization"] = "Bearer " + token
    if files:
        b = uuid.uuid4().hex
        parts = []
        for name, (fname, content, ctype) in files.items():
            parts.append(f'--{b}\r\nContent-Disposition: form-data; name="{name}"; filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n'.encode() + content + b"\r\n")
        data = b"".join(parts) + f"--{b}--\r\n".encode()
        headers["Content-Type"] = "multipart/form-data; boundary=" + b
    elif body is not None:
        data = json.dumps(body).encode(); headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            payload = r.read()
            return r.status, (payload if raw else (json.loads(payload) if payload else None))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try: return e.code, json.loads(payload)
        except Exception: return e.code, payload

def check(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print(("PASS " if cond else "FAIL ") + name + (f"  [{detail}]" if detail and not cond else ""))

def login(email, pw):
    s, r = call("POST", "/auth/login", body={"email": email, "password": pw})
    return s, r

def first_login(email, temp, new):
    s, r = login(email, temp)
    assert s == 200, (email, s, r)
    t = r["token"]
    s2, _ = call("PUT", "/account/password", t, {"currentPassword": temp, "newPassword": new})
    assert s2 == 200, (email, s2)
    return t

def png(rgb):
    w = h = 8
    raw = b"".join(b"\x00" + bytes(rgb) * w for _ in range(h))
    c = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + c(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + c(b"IDAT", zlib.compress(raw)) + c(b"IEND", b"")

today = datetime.date.today()
iso_day = lambda d: datetime.datetime.combine(d, datetime.time()).astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")

# ---------------------------------------------------------------- 1. admin first sign-in
print("\n== Admin sign-in and password gate")
s, r = login("admin@accuratepowertech.local", "ChangeMe@123")
check("seed admin signs in", s == 200 and r["portal"] == "ADMIN")
check("seed admin must change password", r["mustChangePassword"] is True)
tmp = r["token"]
s, r = call("GET", "/quotations", tmp)
check("locked account blocked from data (403 PASSWORD_CHANGE_REQUIRED)", s == 403 and r.get("message") == "PASSWORD_CHANGE_REQUIRED", (s, r))
s, _ = call("PUT", "/account/password", tmp, {"currentPassword": "wrong-one", "newPassword": "Admin@2026x"})
check("wrong current password refused", s == 403)
s, _ = call("PUT", "/account/password", tmp, {"currentPassword": "ChangeMe@123", "newPassword": "short"})
check("short new password refused", s == 400)
s, _ = call("PUT", "/account/password", tmp, {"currentPassword": "ChangeMe@123", "newPassword": "Admin@2026x"})
check("admin sets own password", s == 200)
ADMIN = tmp
s, me = call("GET", "/me", ADMIN)
check("admin unlocked after change", s == 200 and me["mustChangePassword"] is False and me["portal"] == "ADMIN")

# ---------------------------------------------------------------- 2. company + standard text
print("\n== Company settings and standard text")
logo = call("POST", "/files", ADMIN, files={"file": ("logo.png", png((20, 60, 160)), "image/png")})[1]["key"]
cover = call("POST", "/files", ADMIN, files={"file": ("cover.png", png((240, 180, 30)), "image/png")})[1]["key"]
s, c = call("PUT", "/company", ADMIN, {"companyName": "Accurate Powertech India Pvt Ltd", "address": "Plot 12, MIDC Bhosari, Pune 411026",
        "logoKey": logo, "coverImageKey": cover, "diagramImageKey": None, "signatoryName": "Rahul Deshmukh",
        "signatoryTitle": "Director", "signatoryPhone": "9822011223", "signatoryEmail": "rahul@accuratepowertech.local"})
check("company details saved with logo and cover", s == 200 and c["logoKey"] == logo and c["companyName"].startswith("Accurate"))
s, c = call("PUT", "/company", ADMIN, {"companyName": "Accurate Powertech India Pvt Ltd", "logoKey": "1/../2/x.jpg"})
check("foreign/invalid logo key dropped", s == 200 and c["logoKey"] is None)
call("PUT", "/company", ADMIN, {"companyName": "Accurate Powertech India Pvt Ltd", "address": "Plot 12, MIDC Bhosari, Pune 411026", "logoKey": logo,
     "coverImageKey": cover, "signatoryName": "Rahul Deshmukh", "signatoryTitle": "Director", "signatoryPhone": "9822011223", "signatoryEmail": "rahul@accuratepowertech.local"})
s, secs = call("GET", "/sections", ADMIN)
check("standard sections created for the company", s == 200 and len(secs) >= 5, len(secs) if s == 200 else s)
keys = {x["sectionKey"] for x in secs}
pay = next(x for x in secs if x["sectionKey"] == "PAYMENT")
pay["content"] = "50% advance with purchase order\n40% on material delivery\n10% after commissioning"
s, secs = call("PUT", "/sections", ADMIN, secs)
check("standard text edited", s == 200 and any("50% advance" in (x["content"] or "") for x in secs))
s, warr = call("POST", "/sections", ADMIN, {"title": "Warranty", "content": "Panels: 25 years performance\nInverter: 10 years"})
s2, tmpsec = call("POST", "/sections", ADMIN, {"title": "Temporary", "content": "remove me"})
call("DELETE", f"/sections/{tmpsec['id']}", ADMIN)
s, secs = call("GET", "/sections", ADMIN)
titles = [x["title"] for x in secs]
check("custom section added and another deleted", "Warranty" in titles and "Temporary" not in titles)
s, tpl = call("GET", "/quotations/template", ADMIN)
check("new statement template includes Warranty and BOM/OFFER", s == 200 and any(x["title"] == "Warranty" for x in tpl)
      and {"BOM", "OFFER"} <= {x["kind"] for x in tpl})

# ---------------------------------------------------------------- 3. roles
print("\n== Roles")
s, roles = call("GET", "/admin/roles", ADMIN)
rid = {r["name"]: r["id"] for r in roles}
check("built-in roles exist", {"Admin", "Manager", "Sales Executive"} <= set(rid))
s, viewer = call("POST", "/admin/roles", ADMIN, {"name": "Auditor", "description": "Read-only, all quotations", "quotationScope": "ALL",
                                                   "rights": ["QUOTATIONS:VIEW", "PDF:VIEW", "TEMPLATE_DATA:VIEW"]})
check("custom role Auditor created", s == 200 and viewer["quotationScope"] == "ALL" and len(viewer["rights"]) == 3)
rid["Auditor"] = viewer["id"]
s, _ = call("POST", "/admin/roles", ADMIN, {"name": "auditor", "quotationScope": "OWN", "rights": []})
check("duplicate role name (case-insensitive) refused", s == 409)
s, _ = call("POST", "/admin/roles", ADMIN, {"name": "Bad", "rights": ["USERS:VIEW"]})
check("admin-only right refused on a custom role", s == 400)
s, _ = call("POST", "/admin/roles", ADMIN, {"name": "Bad2", "rights": ["NOPE:VIEW"]})
check("unknown right refused", s == 400)
s, _ = call("PUT", f"/admin/roles/{rid['Admin']}", ADMIN, {"name": "Boss", "rights": []})
check("built-in Admin role cannot be edited", s == 400)
s, spare = call("POST", "/admin/roles", ADMIN, {"name": "Spare", "quotationScope": "OWN", "rights": ["QUOTATIONS:VIEW"]})

# ---------------------------------------------------------------- 4. users (single)
print("\n== Users: single create")
people = {}
def add(name, email, mobile, role, desig="", dept=""):
    s, r = call("POST", "/admin/users", ADMIN, {"name": name, "email": email, "mobile": mobile, "designation": desig, "department": dept, "roleId": rid[role]})
    if s == 200: people[email] = {"id": r["user"]["id"], "temp": r["tempPassword"], "name": name}
    return s, r
s, r = add("Priya Kulkarni", "priya@accuratepowertech.local", "9811100001", "Manager", "Sales Manager", "Sales")
check("manager created with temp password", s == 200 and len(r["tempPassword"]) == 10 and r["user"]["mustChangePassword"])
check("sales exec Amit created", add("Amit Patil", "amit@accuratepowertech.local", "9811100002", "Sales Executive", "Executive", "Sales")[0] == 200)
check("sales exec Sneha created", add("Sneha Joshi", "sneha@accuratepowertech.local", "9811100003", "Sales Executive", "Executive", "Sales")[0] == 200)
check("auditor Vikram created", add("Vikram Rao", "vikram@accuratepowertech.local", "9811100004", "Auditor", "Accounts", "Finance")[0] == 200)
check("duplicate email refused", add("X", "AMIT@accuratepowertech.local", "9811100099", "Manager")[0] == 409)
check("duplicate mobile refused", add("X", "x1@accuratepowertech.local", "+91 98111 00002", "Manager")[0] == 409)
check("invalid email refused", add("X", "not-an-email", "9811100098", "Manager")[0] == 400)
check("bad mobile refused", add("X", "x2@accuratepowertech.local", "12ab", "Manager")[0] == 400)
s, _ = call("POST", "/admin/users", ADMIN, {"name": "X", "email": "x3@accuratepowertech.local", "mobile": "9811100097", "roleId": None})
check("missing role refused", s == 400)

# ---------------------------------------------------------------- 5. bulk import
print("\n== Users: bulk import")
s, tpl_csv = call("GET", "/admin/users/import/template", ADMIN, params={"format": "csv"}, raw=True)
check("CSV template downloads", s == 200 and tpl_csv.startswith(b"Name,Email,Mobile"))
s, tpl_x = call("GET", "/admin/users/import/template", ADMIN, params={"format": "xlsx"}, raw=True)
check("Excel template downloads", s == 200 and tpl_x[:2] == b"PK")
rows = [["Name", "Email", "Mobile", "Designation", "Department", "Role"],
        ["Kiran More", "kiran@accuratepowertech.local", "9822200001", "Engineer", "Projects", "Sales Executive"],
        ["Neha Shah", "neha@accuratepowertech.local", "9822200002", "Engineer", "Projects", "Manager"],
        ["Omkar Pawar", "omkar@accuratepowertech.local", "9822200003", "Trainee", "Projects", "auditor"],
        ["Dup In File", "kiran@accuratepowertech.local", "9822200004", "", "", "Manager"],
        ["Existing", "amit@accuratepowertech.local", "9822200005", "", "", "Manager"],
        ["Bad Mobile", "badmob@accuratepowertech.local", "12", "", "", "Manager"],
        ["Bad Role", "badrole@accuratepowertech.local", "9822200006", "", "", "Director"],
        ["", "noname@accuratepowertech.local", "9822200007", "", "", "Manager"],
        ["Dup Mobile", "dupmob@accuratepowertech.local", "+91 98111 00001", "", "", "Manager"]]
buf = io.StringIO(); csv.writer(buf).writerows(rows)
f = {"file": ("users.csv", buf.getvalue().encode(), "text/csv")}
s, rep = call("POST", "/admin/users/import", ADMIN, params={"commit": "false"}, files=f)
check("import check: 9 rows, 3 valid, 6 invalid", s == 200 and (rep["total"], rep["valid"], rep["invalid"]) == (9, 3, 6), (rep.get("total"), rep.get("valid"), rep.get("invalid")))
errs = {r["email"]: r["errors"] for r in rep["rows"]}
check("import errors name the problem", any("Duplicate email within the file" in e for e in errs["kiran@accuratepowertech.local"] if e) or
      "Duplicate email within the file" in errs.get("kiran@accuratepowertech.local", []) or
      any("Duplicate email within the file" in x for r in rep["rows"] for x in r["errors"]))
check("import flags existing email", "Email is already registered" in errs["amit@accuratepowertech.local"])
check("import flags unknown role", any("does not exist" in x for x in errs["badrole@accuratepowertech.local"]))
check("import flags existing mobile", any("already used" in x for x in errs["dupmob@accuratepowertech.local"]))
s, _ = call("GET", "/admin/users", ADMIN)
check("check-only import created nobody", len(_) == 5, len(_))
s, rep = call("POST", "/admin/users/import", ADMIN, params={"commit": "true"}, files=f)
check("import commit created 3 users with temp passwords", s == 200 and rep["created"] == 3 and sum(1 for r in rep["rows"] if r["tempPassword"]) == 3)
for r in rep["rows"]:
    if r["tempPassword"]: people[r["email"]] = {"temp": r["tempPassword"], "name": r["name"]}
s, users = call("GET", "/admin/users", ADMIN)
uid = {u["email"]: u["id"] for u in users}
for e in people: people[e]["id"] = uid[e]
check("8 users in the company now", len(users) == 8, len(users))
check("imported 'auditor' matched role Auditor case-insensitively", next(u for u in users if u["email"] == "omkar@accuratepowertech.local")["roleName"] == "Auditor")

# ---------------------------------------------------------------- 6. first sign-in for everyone
print("\n== First sign-in")
tok = {}
for e, p in people.items():
    tok[e] = first_login(e, p["temp"], "Welcome@2026")
check("every new user set a password", len(tok) == 7)
s, r = login("amit@accuratepowertech.local", "Welcome@2026")
check("sales exec signs into EMPLOYEE portal", s == 200 and r["portal"] == "EMPLOYEE" and not r["mustChangePassword"])
AMIT, SNEHA, PRIYA, VIKRAM = (tok[f"{n}@accuratepowertech.local"] for n in ("amit", "sneha", "priya", "vikram"))

# ---------------------------------------------------------------- 7. quotations by different people
print("\n== Quotations")
def quote(token, client, loc, kw, items, phone="9876500000"):
    s, t = call("GET", "/quotations/template", token)
    body = {"clientName": client, "clientAddress": f"{loc}, Maharashtra", "contactPerson": "Mr. Shinde", "phone": phone,
            "projectLocation": loc, "capacityKw": kw, "gstPercent": 8.9, "showItemPrices": True, "sections": t,
            "items": [{"equipment": e, "specification": sp, "make": mk, "uom": "Nos.", "qty": q, "unitPrice": p, "imageKey": img} for e, sp, mk, q, p, img in items]}
    return call("POST", "/quotations", token, body)
panel = call("POST", "/files", AMIT, files={"file": ("panel.png", png((30, 30, 90)), "image/png")})[1]["key"]
s, q1 = quote(AMIT, "Shree Sai Industries", "Chakan", 50, [("Mono PERC Panel 540W", "Bifacial, 144 cells", "Waaree", 93, 14500, panel),
                                                         ("String Inverter 50kW", "3-phase", "Sungrow", 1, 285000, None)])
check("Amit creates quotation with photo", s == 200 and q1["refNo"].startswith("QTN/") and q1["items"][0]["imageKey"] == panel, s)
check("totals: subtotal, 8.9% GST, total", s == 200 and abs(q1["subtotal"] - 1633500) < 0.01 and abs(q1["gstAmount"] - 145381.50) < 0.01 and abs(q1["total"] - 1778881.50) < 0.01,
      (q1.get("subtotal"), q1.get("gstAmount"), q1.get("total")))
s, q2 = quote(AMIT, "Green Valley School", "Hinjewadi", 20, [("Mono PERC Panel 540W", "Bifacial", "Waaree", 37, 14500, None)])
s, q3 = quote(SNEHA, "Patil Agro Foods", "Baramati", 100, [("Mono PERC Panel 550W", "TOPCon", "Adani", 182, 15200, None),
                                                           ("Structure", "Hot-dip galvanised", "Local", 1, 450000, None)])
s, q4 = quote(PRIYA, "Metro Hospital", "Kothrud", 75, [("String Inverter 75kW", "3-phase", "Growatt", 1, 390000, None)])
check("4 quotations created by 3 people", all(x.get("id") for x in (q1, q2, q3, q4)))
refs = [q1["refNo"], q2["refNo"], q3["refNo"], q4["refNo"]]
check("reference numbers unique and sequential", refs == [f"QTN/{today.year}/{n}" for n in (1, 2, 3, 4)], refs)
s, _ = quote(AMIT, "Bad Phone", "Pune", 10, [], phone="98765-4321")
check("phone with symbols refused", s == 400)
s, _ = call("POST", "/quotations", AMIT, {"clientName": "Neg", "capacityKw": -5})
check("negative capacity refused", s == 400)
s, stolen = call("POST", "/quotations", SNEHA, {"clientName": "Sneha photo test", "items": [{"equipment": "x", "qty": 1, "unitPrice": 1, "imageKey": "1/../" + panel}]})
check("traversal image key not stored on a quotation", s == 200 and stolen["items"][0]["imageKey"] is None)
call("DELETE", f"/quotations/{stolen['id']}", ADMIN)

# ---------------------------------------------------------------- 8. visibility (OWN vs ALL)
print("\n== Visibility by role")
ids = lambda t: sorted(x["id"] for x in call("GET", "/quotations", t)[1])
check("Amit (OWN) sees only his 2", ids(AMIT) == sorted([q1["id"], q2["id"]]), ids(AMIT))
check("Sneha (OWN) sees only her 1", ids(SNEHA) == [q3["id"]])
check("Priya (Manager, ALL) sees all 4", len(ids(PRIYA)) == 4)
check("Vikram (Auditor, ALL) sees all 4", len(ids(VIKRAM)) == 4)
check("Admin sees all 4", len(ids(ADMIN)) == 4)
check("Amit opening Sneha's quotation gets 404", call("GET", f"/quotations/{q3['id']}", AMIT)[0] == 404)
check("Amit editing Sneha's quotation gets 404", call("PUT", f"/quotations/{q3['id']}", AMIT, {"clientName": "hack"})[0] == 404)
s, lst = call("GET", "/quotations", PRIYA)
check("list shows who created each", {x["createdBy"] for x in lst} == {"Amit Patil", "Sneha Joshi", "Priya Kulkarni"})
s, d = call("GET", "/dashboard", AMIT)
check("Amit's dashboard counts only his", s == 200 and d["totalCount"] == 2)
s, d = call("GET", "/dashboard", ADMIN)
check("Admin dashboard counts all, 4 distinct clients", s == 200 and d["totalCount"] == 4 and d["clients"] == 4)
s, cat = call("GET", "/catalog", SNEHA)
check("catalog offers equipment used before", s == 200 and any(x["equipment"] == "Mono PERC Panel 540W" for x in cat))

# ---------------------------------------------------------------- 9. rights
print("\n== Rights and per-user grants/revokes")
check("Auditor cannot create", call("POST", "/quotations", VIKRAM, {"clientName": "x"})[0] == 403)
check("Auditor cannot edit", call("PUT", f"/quotations/{q1['id']}", VIKRAM, {"clientName": "x"})[0] == 403)
check("Auditor can preview PDF", call("GET", f"/quotations/{q1['id']}/pdf", VIKRAM, params={"inline": "true"}, raw=True)[0] == 200)
check("Auditor cannot download PDF", call("GET", f"/quotations/{q1['id']}/pdf", VIKRAM, raw=True)[0] == 403)
check("Sales exec cannot delete", call("DELETE", f"/quotations/{q2['id']}", AMIT)[0] == 403)
check("Sales exec cannot open admin APIs", call("GET", "/admin/users", AMIT)[0] == 403)
check("Manager cannot open admin APIs", call("GET", "/admin/roles", PRIYA)[0] == 403)
s, det = call("GET", f"/admin/users/{people['amit@accuratepowertech.local']['id']}", ADMIN)
want = sorted(set(det["permissions"]) | {"QUOTATIONS:DELETE"})
s, det = call("PUT", f"/admin/users/{people['amit@accuratepowertech.local']['id']}/permissions", ADMIN, {"rights": want})
check("grant QUOTATIONS:DELETE to Amit recorded as a grant", s == 200 and det["grants"] == ["QUOTATIONS:DELETE"])
check("Amit can delete after grant", call("DELETE", f"/quotations/{q2['id']}", AMIT)[0] == 200)
s, det = call("GET", f"/admin/users/{people['sneha@accuratepowertech.local']['id']}", ADMIN)
s, det = call("PUT", f"/admin/users/{people['sneha@accuratepowertech.local']['id']}/permissions", ADMIN, {"rights": [x for x in det["permissions"] if x != "PDF:DOWNLOAD"]})
check("revoke PDF:DOWNLOAD from Sneha recorded as a revoke", s == 200 and det["revokes"] == ["PDF:DOWNLOAD"])
check("Sneha cannot download after revoke", call("GET", f"/quotations/{q3['id']}/pdf", SNEHA, raw=True)[0] == 403)
s, pdf = call("GET", f"/quotations/{q3['id']}/pdf", PRIYA, raw=True)
check("Manager downloads a real PDF", s == 200 and pdf[:4] == b"%PDF" and len(pdf) > 3000)
s, _ = call("PUT", f"/admin/users/{people['sneha@accuratepowertech.local']['id']}/permissions", ADMIN, {"rights": ["USERS:VIEW"]})
check("admin-only right cannot be granted to a user", s == 400)
s, det = call("POST", f"/admin/users/{people['sneha@accuratepowertech.local']['id']}/permissions/reset", ADMIN)
check("reset to role defaults clears grants and revokes", s == 200 and not det["grants"] and not det["revokes"])

# ---------------------------------------------------------------- 10. account lifecycle
print("\n== Deactivate, reset password, role change")
sid = people["sneha@accuratepowertech.local"]["id"]
s, u = call("POST", f"/admin/users/{sid}/active", ADMIN, {"active": False})
check("deactivate Sneha", s == 200 and u["active"] is False)
check("Sneha's open session stops working (401)", call("GET", "/quotations", SNEHA)[0] == 401)
s, r = login("sneha@accuratepowertech.local", "Welcome@2026")
check("deactivated user cannot sign in (403)", s == 403)
call("POST", f"/admin/users/{sid}/active", ADMIN, {"active": True})
s, r = login("sneha@accuratepowertech.local", "Welcome@2026")
check("reactivated user signs in", s == 200); SNEHA = r["token"]
aid = people["amit@accuratepowertech.local"]["id"]
s, r = call("POST", f"/admin/users/{aid}/reset-password", ADMIN)
check("reset Amit's password gives new temp", s == 200 and len(r["tempPassword"]) == 10)
check("Amit's old session revoked", call("GET", "/me", AMIT)[0] == 401)
check("old password no longer works", login("amit@accuratepowertech.local", "Welcome@2026")[0] == 401)
AMIT = first_login("amit@accuratepowertech.local", r["tempPassword"], "Welcome@2027")
check("Amit back in with new password", call("GET", "/me", AMIT)[0] == 200)
vid = people["vikram@accuratepowertech.local"]["id"]
s, u = call("PUT", f"/admin/users/{vid}", ADMIN, {"name": "Vikram Rao", "email": "vikram@accuratepowertech.local", "mobile": "9811100004",
                                                    "designation": "Accounts", "department": "Finance", "roleId": rid["Sales Executive"]})
check("role change Auditor -> Sales Executive", s == 200 and u["roleName"] == "Sales Executive")
check("role change signs the person out", call("GET", "/me", VIKRAM)[0] == 401)
VIKRAM = login("vikram@accuratepowertech.local", "Welcome@2026")[1]["token"]
check("after role change Vikram sees only own (0)", ids(VIKRAM) == [])
check("admin cannot deactivate self", call("POST", f"/admin/users/{me['id']}/active", ADMIN, {"active": False})[0] == 400)
s, _ = call("PUT", f"/admin/users/{me['id']}", ADMIN, {"name": "Rahul Admin", "email": "admin@accuratepowertech.local", "mobile": "9800000000", "roleId": rid["Manager"]})
check("admin cannot change own role / remove last admin", s == 400)
check("role with users cannot be deleted", call("DELETE", f"/admin/roles/{rid['Sales Executive']}", ADMIN)[0] == 400)
check("unused role deleted", call("DELETE", f"/admin/roles/{spare['id']}", ADMIN)[0] == 200)
s, _ = call("POST", "/auth/logout", PRIYA)
check("sign-out revokes the token", call("GET", "/me", PRIYA)[0] == 401)
PRIYA = login("priya@accuratepowertech.local", "Welcome@2026")[1]["token"]
login("priya@accuratepowertech.local", "wrong-password")

# ---------------------------------------------------------------- 11. activity log filters
print("\n== Activity log filters")
pid = people["priya@accuratepowertech.local"]["id"]
s, ss = call("GET", "/admin/activity/sessions", ADMIN, params={"size": 200})
check("sign-in history recorded", s == 200 and ss["total"] >= 13, ss.get("total"))
s, sp = call("GET", "/admin/activity/sessions", ADMIN, params={"userId": pid, "size": 200})
check("sessions filter by user", s == 200 and sp["total"] >= 2 and all(x["userId"] == pid for x in sp["items"]))
check("sign-out recorded with reason", any(x["signOutReason"] == "SIGN_OUT" for x in sp["items"]))
s, sx = call("GET", "/admin/activity/sessions", ADMIN, params={"userId": sid, "size": 200})
check("deactivation closed Sneha's session with reason", any(x["signOutReason"] == "DEACTIVATED" for x in sx["items"]))
s, sd = call("GET", "/admin/activity/sessions", ADMIN, params={"from": iso_day(today), "to": iso_day(today + datetime.timedelta(days=1)), "size": 200})
check("sessions date filter: today = all", s == 200 and sd["total"] == ss["total"])
s, sy = call("GET", "/admin/activity/sessions", ADMIN, params={"to": iso_day(today), "size": 200})
check("sessions date filter: before today = none", s == 200 and sy["total"] == 0)
s, p1 = call("GET", "/admin/activity/sessions", ADMIN, params={"page": 0, "size": 5})
s, p2 = call("GET", "/admin/activity/sessions", ADMIN, params={"page": 1, "size": 5})
check("sessions pagination", len(p1["items"]) == 5 and len(p2["items"]) == 5 and not ({x["id"] for x in p1["items"]} & {x["id"] for x in p2["items"]}))
s, al = call("GET", "/admin/activity/audit", ADMIN, params={"size": 200})
check("audit trail recorded", s == 200 and al["total"] >= 25, al.get("total"))
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"module": "QUOTATIONS", "action": "CREATE", "size": 200})
check("audit filter module+action (5 creates incl. the dropped test one)", s == 200 and a["total"] == 5 and all(x["module"] == "QUOTATIONS" and x["action"] == "CREATE" for x in a["items"]), a.get("total"))
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"userId": aid, "module": "QUOTATIONS", "size": 200})
check("audit filter by user (Amit: 2 creates + 1 delete)", s == 200 and sorted(x["action"] for x in a["items"]) == ["CREATE", "CREATE", "DELETE"], [x["action"] for x in a["items"]])
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"q": "metro hospital", "size": 200})
check("audit text search (case-insensitive)", s == 200 and a["total"] >= 1 and all("metro hospital" in (x["detail"] or "").lower() for x in a["items"]))
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"module": "AUTH", "action": "LOGIN_FAILED", "size": 200})
check("failed sign-ins logged under AUTH", s == 200 and a["total"] >= 3, a.get("total"))
for act in ("DEACTIVATE", "ACTIVATE", "RESET_PASSWORD", "PERMISSIONS", "IMPORT", "PASSWORD_CHANGE", "DOWNLOAD", "DELETE"):
    s, a = call("GET", "/admin/activity/audit", ADMIN, params={"action": act, "size": 1})
    check(f"audit has {act} entries", s == 200 and a["total"] >= 1)
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"from": iso_day(today + datetime.timedelta(days=1)), "size": 200})
check("audit date filter: tomorrow = none", s == 200 and a["total"] == 0)
s, csvb = call("GET", "/admin/activity/audit.csv", ADMIN, params={"module": "QUOTATIONS"}, raw=True)
lines = csvb.decode("utf-8-sig").strip().splitlines()
s2, a = call("GET", "/admin/activity/audit", ADMIN, params={"module": "QUOTATIONS", "size": 200})
check("CSV export honours filters (rows = filtered count)", s == 200 and len(lines) - 1 == a["total"], (len(lines) - 1, a["total"]))
s, ov = call("GET", "/admin/overview", ADMIN)
check("overview counts users/roles/admins", s == 200 and ov["users"] == 8 and ov["activeUsers"] == 8 and ov["roles"] == 4 and ov["admins"] == 1, ov if s == 200 else s)

# ---------------------------------------------------------------- 11b. Masters > Customers
print("\n== Customers")
cust = lambda token, body: call("POST", "/customers", token, body)
s, c1 = cust(PRIYA, {"name": "Shree Sai Industries", "contactPerson": "Mr. Shinde", "phone": "9876500000", "email": "Purchase@ShreeSai.local",
                     "gstin": "27abcde1234f1z5", "address": "Plot 4, MIDC Chakan", "city": "Chakan", "notes": "Prefers Waaree panels"})
check("manager adds a customer (GSTIN upper-cased, email lower-cased)", s == 200 and c1["gstin"] == "27ABCDE1234F1Z5" and c1["email"] == "purchase@shreesai.local", (s, c1))
s, c2 = cust(SNEHA, {"name": "Patil Agro Foods", "phone": "9822000001", "city": "Baramati"})
check("sales exec adds a customer", s == 200)
s, c3 = cust(PRIYA, {"name": "Metro Hospital", "city": "Kothrud"})
check("duplicate name refused (case-insensitive)", cust(PRIYA, {"name": "shree sai industries"})[0] == 409)
check("missing name refused", cust(PRIYA, {"name": "  "})[0] == 400)
check("bad phone refused", cust(PRIYA, {"name": "X1", "phone": "98-765"})[0] == 400)
check("bad email refused", cust(PRIYA, {"name": "X2", "email": "nope"})[0] == 400)
check("bad GSTIN refused", cust(PRIYA, {"name": "X3", "gstin": "12345"})[0] == 400)
s, lst = call("GET", "/customers", SNEHA)
check("list sorted by name, shared across the company", s == 200 and [x["name"] for x in lst] == ["Metro Hospital", "Patil Agro Foods", "Shree Sai Industries"], [x["name"] for x in lst] if s == 200 else s)
s, u = call("PUT", f"/customers/{c2['id']}", SNEHA, {"name": "Patil Agro Foods Pvt Ltd", "phone": "9822000001", "city": "Baramati"})
check("sales exec edits a customer", s == 200 and u["name"] == "Patil Agro Foods Pvt Ltd")
check("rename onto an existing name refused", call("PUT", f"/customers/{c2['id']}", SNEHA, {"name": "Metro Hospital"})[0] == 409)
check("sales exec cannot delete (no CUSTOMERS:DELETE)", call("DELETE", f"/customers/{c3['id']}", SNEHA)[0] == 403)
check("manager deletes a customer", call("DELETE", f"/customers/{c3['id']}", PRIYA)[0] == 200)
check("deleted customer is gone", call("GET", f"/customers/{c3['id']}", PRIYA)[0] == 404)
s, ar = call("POST", "/admin/roles", ADMIN, {"name": "Reports only", "quotationScope": "ALL", "rights": ["QUOTATIONS:VIEW"]})
s, ru = call("POST", "/admin/users", ADMIN, {"name": "Reena Iyer", "email": "reena@accuratepowertech.local", "mobile": "9811100077", "roleId": ar["id"]})
REENA = first_login("reena@accuratepowertech.local", ru["tempPassword"], "Welcome@2026")
check("no CUSTOMERS rights and no quotation create/edit: list refused", call("GET", "/customers", REENA)[0] == 403)
check("no CUSTOMERS rights: create refused", cust(REENA, {"name": "Y"})[0] == 403)
s, a = call("GET", "/admin/activity/audit", ADMIN, params={"module": "CUSTOMERS", "size": 200})
check("customer changes in the audit trail (3 create, 1 edit, 1 delete)", s == 200 and sorted(x["action"] for x in a["items"]) == ["CREATE", "CREATE", "CREATE", "DELETE", "EDIT"], [x["action"] for x in a["items"]] if s == 200 else s)

# ---------------------------------------------------------------- 12. other company is isolated
print("\n== Tenant isolation")
s, r = call("POST", "/auth/register", body={"email": "owner@othersolar.local", "password": "Other@2026x", "companyName": "Other Solar"})
OTHER = r["token"]
check("second company registered", s == 200 and r["portal"] == "ADMIN")
check("other company sees no quotations", ids(OTHER) == [])
check("other company sees no customers", call("GET", "/customers", OTHER)[1] == [])
check("other company cannot open our customer (404)", call("GET", f"/customers/{c1['id']}", OTHER)[0] == 404)
check("other company cannot edit our customer (404)", call("PUT", f"/customers/{c1['id']}", OTHER, {"name": "hack"})[0] == 404)
check("other company cannot open ours (404)", call("GET", f"/quotations/{q1['id']}", OTHER)[0] == 404)
check("other company cannot open our user (404)", call("GET", f"/admin/users/{aid}", OTHER)[0] == 404)
check("other company cannot read our photo", call("GET", "/files", OTHER, params={"key": panel}, raw=True)[0] == 403)
check("other company cannot read our photo via ../", call("GET", "/files", OTHER, params={"key": f"{r and call('GET','/me',OTHER)[1]['id']}/../{panel}"}, raw=True)[0] == 403)
s, oa = call("GET", "/admin/activity/audit", OTHER, params={"size": 200})
check("other company's audit trail is empty of our data", s == 200 and all("Shree Sai" not in (x["detail"] or "") for x in oa["items"]))

ok = sum(1 for r in results if r[0]); bad = [r for r in results if not r[0]]
print(f"\n{ok}/{len(results)} checks passed")
for r in bad: print("FAILED:", r[1], r[2])
sys.exit(1 if bad else 0)
