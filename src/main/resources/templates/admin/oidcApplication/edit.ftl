<#import "../../layout/defaultLayout.ftl" as layout>
<@layout.layout "Edit OpenID connect application">
  <ol class="breadcrumb">
    <li class="breadcrumb-item"><a href="${urlFor('net.unit8.bouncr.web.controller.admin.IndexController', 'home')}">Administration</a></li>
    <li class="breadcrumb-item"><a href="${urlFor('list')}">OpenID connect Applications</a></li>
    <li class="breadcrumb-item active">Edit</li>
  </ol>
   <h1>Edit OpenID connect application</h1>

   <form method="post" action="${urlFor('update?id=' + oidcApplication.id)}">
     <#include "_form.ftl">
     <button type="submit" class="btn btn-primary">Update</button>
   </form>
</@layout.layout>
