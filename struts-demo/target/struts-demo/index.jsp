<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String qs = request.getQueryString();
    String target = request.getContextPath() + "/login.action"
            + (qs != null && !qs.isEmpty() ? "?" + qs : "");
    response.sendRedirect(target);
%>
