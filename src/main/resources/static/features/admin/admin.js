"use strict";
async function openPage(name) {
  document.body.classList.add('supplemental');
  const version=++navigationVersion;
  await refreshMe();
  const markup = await request("/admin/" + name);
  if(version!==navigationVersion) return;
  // Parse server-owned static fragments only; all user data uses textContent.
  const fragment = new DOMParser().parseFromString(markup, "text/html");
  el("managementPanel").replaceChildren(...fragment.body.childNodes);
  activePage(name);
  renderAccount(currentUser);
  if (name === "users") {
    userPage = 0; userSearch = "";
    formHandler(el("searchUsers"), async data => { userSearch = data.search; userPage = 0; await loadUsers(); });
    formHandler(el("createUser"), async data => {
      await json("/api/v1/users", "POST", data); el("createUser")?.reset(); await loadUsers(); message("Đã tạo tài khoản.");
    });
    el("previousUsers").onclick = () => run(async () => { userPage--; await loadUsers(); });
    el("nextUsers").onclick = () => run(async () => { userPage++; await loadUsers(); });
    await loadUsers();
  } else {
    formHandler(el("createRole"), async data => {
      await json("/api/v1/roles", "POST", data); el("createRole")?.reset(); await loadRoles(); message("Đã tạo vai trò.");
    });
    await loadRoles();
  }
}
async function loadUsers() {
  const version=navigationVersion,list=el('userList');
  if(!list) return;
  const data = await request(`/api/v1/users?page=${userPage}&size=10&search=${encodeURIComponent(userSearch)}`);
  const roles = has("users:assign") && has("roles:read") ? await request("/api/v1/roles") : [];
  if(version!==navigationVersion || !list.isConnected) return;
  list.replaceChildren();
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
  const version=navigationVersion,list=el('roleList');
  if(!list) return;
  const [roles, permissions] = await Promise.all([request("/api/v1/roles"), request("/api/v1/permissions")]);
  if(version!==navigationVersion || !list.isConnected) return;
  list.replaceChildren();
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
