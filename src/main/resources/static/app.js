"use strict";
// Bearer tokens stay in memory; no storage or authentication cookies.
let accessToken = null, currentUser = null, userPage = 0, userSearch = "";
const el = id => document.getElementById(id);
const has = permission => currentUser?.permissions.includes(permission);
const message = text => { el("message").textContent = text; };
function signedOut() {
  accessToken = null; currentUser = null;
  el("loginPanel").hidden = false; el("accountPanel").hidden = true;
  el("managementPanel").replaceChildren(); el("passwordForm").reset();
}
function renderAccount(user) {
  currentUser = user;
  el("welcome").textContent = "Xin chào, " + user.fullName;
  el("accountName").textContent = user.username;
  el("accountRoles").textContent = user.systemRoles.join(", ");
  el("loginPanel").hidden = true; el("accountPanel").hidden = false;
  document.querySelectorAll("[data-permission]").forEach(node => { node.hidden = !has(node.dataset.permission); });
}
async function request(path, options = {}) {
  const session = accessToken;
  const headers = { ...options.headers };
  if (accessToken) headers.Authorization = "Bearer " + accessToken;
  const res = await fetch(path, { ...options, headers, cache: "no-store", credentials: "omit" });
  if (session !== accessToken) throw new Error("Phiên đăng nhập đã thay đổi. Vui lòng thử lại.");
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    if (res.status === 401 && path !== "/api/v1/auth/login") signedOut();
    if (res.status === 403) el("managementPanel").replaceChildren();
    throw new Error((data?.error?.message || "Không thể xử lý yêu cầu.") + " Mã: " + (data?.requestId || "—"));
  }
  if (res.status === 204) return null;
  return res.headers.get("Content-Type")?.includes("text/html") ? res.text() : (await res.json()).data;
}
const json = (path, method, body) => request(path, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
async function run(action, button) {
  if (button) button.disabled = true;
  message("");
  try { await action(); } catch (error) { message(error instanceof TypeError ? "Không kết nối được máy chủ. Vui lòng thử lại." : error.message); }
  finally { if (button) button.disabled = false; }
}
function formHandler(form, action) {
  form.addEventListener("submit", event => {
    event.preventDefault(); run(() => action(Object.fromEntries(new FormData(form))), event.submitter);
  });
}
function node(tag, text) {
  const result = document.createElement(tag);
  if (text !== undefined) result.textContent = text;
  return result;
}
function field(form, title, name, value, type = "text", max = 100) {
  const label = node("label", title), input = node("input");
  input.name = name; input.type = type; input.value = value; input.required = true; input.maxLength = max;
  if (type === "password") { input.minLength = 12; input.autocomplete = "new-password"; }
  label.append(input); form.append(label); return input;
}
function button(parent, title, action) {
  const result = node("button", title); result.type = "button";
  result.addEventListener("click", () => run(action, result)); parent.append(result); return result;
}
function saveForm(parent, title, setup, action) {
  const form = node("form"); setup(form);
  const submit = node("button", title); submit.type = "submit"; form.append(submit);
  formHandler(form, action); parent.append(form);
}
async function refreshMe() { renderAccount((await request("/api/v1/auth/me")).user); }
async function openPage(name) {
  await refreshMe();
  const markup = await request("/admin/" + name);
  // Parse server-owned static fragments only; all user data uses textContent.
  const fragment = new DOMParser().parseFromString(markup, "text/html");
  el("managementPanel").replaceChildren(...fragment.body.childNodes);
  renderAccount(currentUser);
  if (name === "users") {
    userPage = 0; userSearch = "";
    formHandler(el("searchUsers"), async data => { userSearch = data.search; userPage = 0; await loadUsers(); });
    formHandler(el("createUser"), async data => {
      await json("/api/v1/users", "POST", data); el("createUser").reset(); await loadUsers(); message("Đã tạo tài khoản.");
    });
    el("previousUsers").onclick = () => run(async () => { userPage--; await loadUsers(); });
    el("nextUsers").onclick = () => run(async () => { userPage++; await loadUsers(); });
    await loadUsers();
  } else {
    formHandler(el("createRole"), async data => {
      await json("/api/v1/roles", "POST", data); el("createRole").reset(); await loadRoles(); message("Đã tạo vai trò.");
    });
    await loadRoles();
  }
}
async function loadUsers() {
  const data = await request(`/api/v1/users?page=${userPage}&size=10&search=${encodeURIComponent(userSearch)}`);
  const roles = has("users:assign") && has("roles:read") ? await request("/api/v1/roles") : [];
  const list = el("userList"); list.replaceChildren();
  el("userPage").textContent = `Trang ${userPage + 1} · ${data.total} tài khoản`;
  el("previousUsers").disabled = userPage === 0;
  el("nextUsers").disabled = (userPage + 1) * 10 >= data.total;
  for (const user of data.items) {
    const card = node("article");
    card.append(node("h3", `${user.fullName} (${user.username})`), node("p", `${user.email} · ${user.status}`), node("p", user.systemRoles.join(", ")));
    const base = "/api/v1/users/" + user.id;
    if (has("users:update")) saveForm(card, "Lưu thông tin", form => {
      field(form, "Họ tên", "fullName", user.fullName); field(form, "Email", "email", user.email, "email", 254);
    }, async values => { await json(base, "PUT", values); await refreshMe(); await loadUsers(); message("Đã cập nhật thông tin."); });
    if (has("users:status") && user.id !== currentUser.id) button(card, user.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt", async () => {
      await json(base + "/status", "PUT", { status: user.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" }); await loadUsers(); message("Đã cập nhật trạng thái.");
    });
    if (roles.length) {
      const form = node("form"), group = node("fieldset"); group.append(node("legend", "Vai trò hệ thống"));
      for (const role of roles) {
        const label = node("label", role.name), input = node("input"); input.type = "checkbox";
        input.value = role.id; input.checked = user.systemRoles.includes(role.name); label.prepend(input); group.append(label);
      }
      form.append(group); form.append(node("button", "Lưu vai trò")); card.append(form);
      formHandler(form, async () => {
        const roleIds = [...group.querySelectorAll("input:checked")].map(input => Number(input.value));
        await json(base + "/roles", "PUT", { roleIds });
        await refreshMe(); if (has("users:read")) await loadUsers(); else el("managementPanel").replaceChildren();
        message("Đã cập nhật vai trò.");
      });
    }
    if (has("users:password") && user.id !== currentUser.id) saveForm(card, "Đặt lại mật khẩu", form => {
      field(form, "Mật khẩu mới (12–128 ký tự, có chữ và số)", "newPassword", "", "password", 128);
    }, async values => { await json(base + "/password", "PUT", values); await loadUsers(); message("Đã đặt lại mật khẩu và thu hồi mọi phiên."); });
    list.append(card);
  }
  if (!data.items.length) list.append(node("p", "Không tìm thấy người dùng."));
}
async function loadRoles() {
  const [roles, permissions] = await Promise.all([request("/api/v1/roles"), request("/api/v1/permissions")]);
  const list = el("roleList"); list.replaceChildren();
  for (const role of roles) {
    const card = node("article"); card.append(node("h3", role.name), node("p", role.description), node("p", role.permissions.join(", ") || "Chưa có quyền quản trị."));
    if (has("roles:write") && !["SYSTEM_ADMIN", "USER"].includes(role.name)) saveForm(card, "Lưu vai trò", form => {
      field(form, "Tên vai trò", "name", role.name, "text", 50); field(form, "Mô tả", "description", role.description || "", "text", 255);
    }, async values => { await json(`/api/v1/roles/${role.id}`, "PUT", values); await refreshMe(); await loadRoles(); message("Đã cập nhật vai trò."); });
    if (has("roles:permissions") && role.name !== "SYSTEM_ADMIN") {
      const form = node("form"), group = node("fieldset"); group.append(node("legend", "Quyền được cấp"));
      for (const permission of permissions) {
        const label = node("label", permission.code), input = node("input"); input.type = "checkbox";
        input.value = permission.code; input.checked = role.permissions.includes(permission.code); label.prepend(input); group.append(label);
      }
      form.append(group, node("button", "Lưu quyền")); card.append(form);
      formHandler(form, async () => {
        await json(`/api/v1/roles/${role.id}/permissions`, "PUT", { permissions: [...group.querySelectorAll("input:checked")].map(input => input.value) });
        await refreshMe(); if (has("roles:read")) await loadRoles(); else el("managementPanel").replaceChildren();
        message("Đã cập nhật quyền.");
      });
    }
    list.append(card);
  }
}
el("showPassword").addEventListener("change", event => { el("password").type = event.target.checked ? "text" : "password"; });
formHandler(el("loginForm"), async data => {
  const result = await json("/api/v1/auth/login", "POST", { username: data.username.trim(), password: data.password });
  accessToken = result.accessToken; el("loginForm").reset(); el("password").type = "password";
  renderAccount(result.user); message("Đăng nhập thành công.");
});
formHandler(el("passwordForm"), async data => {
  await json("/api/v1/auth/password", "POST", data); signedOut(); message("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
});
el("checkButton").onclick = () => run(async () => { await refreshMe(); el("managementPanel").replaceChildren(); message("Phiên đăng nhập còn hiệu lực."); });
el("logoutButton").onclick = () => run(async () => { await request("/api/v1/auth/logout", { method: "POST" }); signedOut(); message("Đã đăng xuất."); }, el("logoutButton"));
el("usersButton").onclick = () => run(() => openPage("users"), el("usersButton"));
el("rolesButton").onclick = () => run(() => openPage("roles"), el("rolesButton"));
