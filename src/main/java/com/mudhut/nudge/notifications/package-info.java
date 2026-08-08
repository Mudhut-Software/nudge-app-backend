// CLOSED module: leaf — nothing imports it. Listeners consume servicerequests.events and
// invoices.events; the invoice listener also depends on users (repositories/entities named
// interfaces) to resolve the customer and on email (IEmailService) to send.
@org.springframework.modulith.ApplicationModule
package com.mudhut.nudge.notifications;
