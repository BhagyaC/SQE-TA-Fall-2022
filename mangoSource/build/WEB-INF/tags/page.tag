<%--
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/.
--%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<%@include file="/WEB-INF/tags/decl.tagf"%>
<%@attribute name="styles" fragment="true" %>
<%@attribute name="dwr" %>
<%@attribute name="js" %>
<%@attribute name="onload" %>

<html>
<head>
  <title><c:choose>
    <c:when test="${!empty instanceDescription}">${instanceDescription}</c:when>
    <c:otherwise><fmt:message key="header.title"/></c:otherwise>
  </c:choose></title>
  
  <!-- Meta -->
  <meta http-equiv="content-type" content="application/xhtml+xml;charset=utf-8"/>
  <meta http-equiv="Content-Style-Type" content="text/css" />
  <meta name="Copyright" content="&copy;2006-2011 Serotonin Software Technologies Inc."/>
  <meta name="DESCRIPTION" content="Mango Serotonin Software"/>
  <meta name="KEYWORDS" content="Mango Serotonin Software"/>
  
  <!-- Style -->
  <link rel="icon" href="images/favicon.ico"/>
  <link rel="shortcut icon" href="images/favicon.ico"/>
  <link href="resources/common.css" type="text/css" rel="stylesheet"/>
  <jsp:invoke fragment="styles"/>
  
  <!-- Scripts -->
  <script type="text/javascript">var djConfig = { isDebug: false, extraLocale: ['en-us', 'nl', 'nl-nl', 'ja-jp', 'fi-fi', 'sv-se', 'zh-cn', 'zh-tw','xx'] };</script>
  <!-- script type="text/javascript" src="http://o.aolcdn.com/dojo/0.4.2/dojo.js"></script -->
  <script type="text/javascript" src="resources/dojo/dojo.js"></script>
  <script type="text/javascript" src="dwr/engine.js"></script>
  <script type="text/javascript" src="dwr/util.js"></script>
  <script type="text/javascript" src="dwr/interface/MiscDwr.js"></script>
  <script type="text/javascript" src="resources/soundmanager2-nodebug-jsmin.js"></script>
  <script type="text/javascript" src="resources/common.js"></script>
  <c:forEach items="${dwr}" var="dwrname">
    <script type="text/javascript" src="dwr/interface/${dwrname}.js"></script></c:forEach>
  <c:forEach items="${js}" var="jsname">
    <script type="text/javascript" src="resources/${jsname}.js"></script></c:forEach>
  <script type="text/javascript">
    mango.i18n = <sst:convert obj="${clientSideMessages}"/>;
  </script>
  <c:if test="${!simple}">
    <script type="text/javascript" src="resources/header.js"></script>
    <script type="text/javascript">
      dwr.util.setEscapeHtml(false);
      <c:if test="${!empty sessionUser}">
        dojo.addOnLoad(mango.header.onLoad);
        dojo.addOnLoad(function() { setUserMuted(${sessionUser.muted}); });
      </c:if>
      
      function setLocale(locale) {
          MiscDwr.setLocale(locale, function() { window.location = window.location });
      }
      
      function setHomeUrl() {
          MiscDwr.setHomeUrl(window.location.href, function() { alert("Home URL saved"); });
      }
      
      function goHomeUrl() {
          MiscDwr.getHomeUrl(function(loc) { window.location = loc; });
      }
    </script>
  </c:if>
</head>

<body>
<table width="100%" cellspacing="0" cellpadding="0" border="0" id="mainHeader">
  <tr>
    <td><img src="images/mangoLogoMed.jpg" alt="Logo"/></td>
    <c:if test="${!simple}">
      <td align="center" width="99%">
        <a href="events.shtm">
          <span id="__header__alarmLevelDiv" style="display:none;">
            <img id="__header__alarmLevelImg" src="images/spacer.gif" alt="" border="0" title=""/>
            <span id="__header__alarmLevelText"></span>
          </span>
        </a>
      </td>
    </c:if>
    <c:if test="${!empty instanceDescription}">
      <td align="right" valign="bottom" class="smallTitle" style="padding:5px; white-space: nowrap;">${instanceDescription}</td>
    </c:if>
  </tr>
</table>

<c:if test="${!simple}">
  <table width="100%" cellspacing="0" cellpadding="0" border="0" id="subHeader">
    <tr>
      <td style="cursor:default" >
        <c:if test="${!empty sessionUser}">
          <tag:menuItem href="watch_list.shtm" png="eye" key="header.watchlist"/>
          <tag:menuItem href="views.shtm" png="icon_view" key="header.views"/>
          <tag:menuItem href="events.shtm" png="flag_white" key="header.alarms"/>
          <tag:menuItem href="reports.shtm" png="report" key="header.reports"/>
                
          <c:if test="${sessionUser.dataSourcePermission}">
            <img src="images/menu_separator.png"/>
            <tag:menuItem href="event_handlers.shtm" png="cog" key="header.eventHandlers"/>
            <tag:menuItem href="data_sources.shtm" png="icon_ds" key="header.dataSources"/>
            <tag:menuItem href="scheduled_events.shtm" png="clock" key="header.scheduledEvents"/>
            <tag:menuItem href="compound_events.shtm" png="multi_bell" key="header.compoundEvents"/>
            <tag:menuItem href="point_links.shtm" png="link" key="header.pointLinks"/>
          </c:if>
          
          <img src="images/menu_separator.png"/>
          <tag:menuItem href="users.shtm" png="user" key="header.users"/>
          
          <c:if test="${sessionUser.admin}">
            <tag:menuItem href="point_hierarchy.shtm" png="folder_brick" key="header.pointHierarchy"/>
            <tag:menuItem href="mailing_lists.shtm" png="book" key="header.mailingLists"/>
            <tag:menuItem href="publishers.shtm" png="transmit" key="header.publishers"/>
            <tag:menuItem href="maintenance_events.shtm" png="hammer" key="header.maintenanceEvents"/>
            <tag:menuItem href="system_settings.shtm" png="application_form" key="header.systemSettings"/>
            <tag:menuItem href="emport.shtm" png="script_code" key="header.emport"/>
            <tag:menuItem href="sql.shtm" png="script" key="header.sql"/>
          </c:if>
          
          <img src="images/menu_separator.png"/>
          <tag:menuItem href="logout.htm" png="control_stop_blue" key="header.logout"/>
          <tag:menuItem href="help.shtm" png="help" key="header.help"/>
        </c:if>
        <c:if test="${empty sessionUser}">
          <tag:menuItem href="login.htm" png="control_play_blue" key="header.login"/>
        </c:if>
        <div id="headerMenuDescription" class="labelDiv" style="position:absolute;display:none;"></div>
      </td>
      
      <td align="right">
        <c:if test="${!empty sessionUser}">
          <span class="copyTitle"><fmt:message key="header.user"/>: <b>${sessionUser.username}</b></span>
          <tag:img id="userMutedImg" onclick="MiscDwr.toggleUserMuted(setUserMuted)" onmouseover="hideLayer('localeEdit')"/>
          <tag:img png="house" title="header.goHomeUrl" onclick="goHomeUrl()" onmouseover="hideLayer('localeEdit')"/>
          <tag:img png="house_link" title="header.setHomeUrl" onclick="setHomeUrl()" onmouseover="hideLayer('localeEdit')"/>
        </c:if>
        <div style="display:inline;" onmouseover="showMenu('localeEdit', -40, 10);">
          <tag:img png="world" title="header.changeLanguage"/>
          <div id="localeEdit" style="visibility:hidden;left:0px;top:15px;" class="labelDiv" onmouseout="hideLayer(this)">
            <c:forEach items="${availableLanguages}" var="lang">
              <a class="ptr" onclick="setLocale('${lang.key}')">${lang.value}</a><br/>
            </c:forEach>
          </div>
        </div>
      </td>
    </tr>
  </table>
</c:if>

<div style="padding:5px;">
  <jsp:doBody/>
</div>
<table width="100%" cellspacing="0" cellpadding="0" border="0">
  <tr><td colspan="2">&nbsp;</td></tr>
  <tr>
    <td colspan="2" class="footer" align="center">&copy;2006-2011 Serotonin Software Technologies Inc., <fmt:message key="footer.rightsReserved"/></td>
  </tr>
</table>
<c:if test="${!empty onload}">
  <script type="text/javascript">dojo.addOnLoad(${onload});</script>
</c:if>

</body>
</html>