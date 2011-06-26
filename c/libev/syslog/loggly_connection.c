#include "loggly_connection.h"

void loggly_input_connection_stop(struct ev_loop *loop,
                                  loggly_input_connection *connection) {
  //printf("Closing %d\n", connection->io.fd);
  ev_io_stop(loop, &connection->io);
  close(connection->io.fd);
  loggly_input_connection_free(connection);
} /* loggly_input_connection_stop */

void loggly_input_connection_free(loggly_input_connection *connection) {
  //printf("Freeing %x\n", (void *) connection->buffer);
  if (connection != NULL) {
    if (connection->buffer != NULL) {
      free(connection->buffer);
      connection->buffer = NULL;
      connection->buffer_len = 0;
    }
    free(connection);
  }
} /* loggly_input_connection_free */

void loggly_input_stream_cb(struct ev_loop *loop, ev_io *watcher, int revents) {
  loggly_input_connection *connection = (loggly_input_connection*)watcher;
  //printf("Watcher %d ready\n", watcher->fd);

  int rc;
  ssize_t bytes;
  int done = 0;

  while (!done) {
    bytes = read(watcher->fd, connection->buffer, connection->buffer_len);
    if (bytes == 0) {
      loggly_input_connection_stop(EV_A_ connection); /* EOF, close it. */
      done = 1;
    } else if (bytes < 0) {
      done = 1;
      if (errno == EAGAIN) {
        /* read would block, finished reading for now. */
      } else {
        /* Some other error, close this connection */
        loggly_input_connection_stop(EV_A_ connection);
      } /* errno checking */
    } /* bytes checking */

    /* Get here, and we have data. */
    printf("Received %d bytes: '%.*s'\n", bytes, bytes, connection->buffer);
  } /* while true */
} /* loggly_input_stream_cb */

void loggly_input_datagram_cb(struct ev_loop *loop, ev_io *watcher,
                              int revents) {
  /* Nothing to do yet. UDP support and whatnot. */
}

