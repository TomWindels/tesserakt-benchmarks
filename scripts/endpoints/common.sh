#! /bin/bash

EXITING=false

on_exit_requested() {
	EXITING=true
	echo "Exit requested, shutting down..."
}

on_endpoint_ready() {
	echo "Waiting until the port ${ENDPOINT_PORT} becomes available"
	sleep 2
	{
	  while ! echo -n > /dev/tcp/localhost/${ENDPOINT_PORT}; do
	    echo -n "."
	    sleep 2
	  done
	} 2>/dev/null

	trap "echo;on_exit_requested" SIGINT

	printf "\nEndpoint is ready: %s\n" "$1"

	echo "Use ^C / SIGINT to initiate shutdown, e.g. \`kill -SIGINT $(echo $$)\`"

	while [[ $EXITING == false ]]; do
		sleep 1
	done
}
