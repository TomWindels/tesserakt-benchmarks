#! /bin/bash

export EXITING=false

on_exit_requested() {
	export EXITING=true
	echo "Exit requested, shutting down..."
}

await_port_in_use() {
	trap "echo;on_exit_requested" SIGINT

	echo "Waiting until the port ${ENDPOINT_PORT} is in use"
	sleep 2
	{
		while [ $EXITING == false ] && ! echo -n > /dev/tcp/localhost/${ENDPOINT_PORT}; do
			echo -n "."
			sleep 2
		done
	} 2>/dev/null

	{
		if echo -n > /dev/tcp/localhost/${ENDPOINT_PORT}; then
			echo "Port ${ENDPOINT_PORT} is in use!"
		fi
	} 2>/dev/null
}

on_endpoint_ready() {
	# Making sure the endpoint is actually present on the port number we have
	# defined in the env variable
	await_port_in_use

	if [[ $EXITING == true ]]; then
		exit 1
	fi
	
	trap "echo;on_exit_requested" SIGINT

	printf "\nEndpoint is ready: %s\n" "$1"

	echo "Use ^C / SIGINT to initiate shutdown, e.g. \`kill -SIGINT $(echo $$)\`"

	while [[ $EXITING == false ]]; do
		sleep 1
	done
}
