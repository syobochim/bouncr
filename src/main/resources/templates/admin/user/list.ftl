<#import "../../layout/defaultLayout.ftl" as layout>
<@layout.layout "List of users">
  <ol class="breadcrumb">
    <li class="breadcrumb-item"><a href="${urlFor('net.unit8.bouncr.web.controller.admin.IndexController', 'home')}">Administration</a></li>
    <li class="breadcrumb-item active">Users</li>
  </ol>
  <h1>List of users</h1>

  <#list users>
  <table class="table">
    <thead>
      <tr>
        <th>${t('field.account')}</th>
        <th>${t('field.name')}</th>
      </tr>
    </thead>
    <tbody>
      <#items as user>
        <tr>
          <td>
            <a href="${urlFor('show?id=' + user.id)}">${user.account}</a>
          </td>
          <td>${user.name}</td>
        </tr>
      </#items>
    </tbody>
  </table>
  <#else>
  <div class="alert alert-info" role="alert">
     <p>No users</p>
  </div>
  </#list>

  <#if hasAnyPermissions(userPrincipal, "CREATE_USER")>
  <a href="${urlFor('newUser')}">New register</a>
  </#if>
</@layout.layout>
