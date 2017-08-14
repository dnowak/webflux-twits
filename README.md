# WebFlux Twits

Sample project that mimics Twitter functionality.

Based on WebFlux functional web framework from Spring.

EventStore as a basic data store.

## Functionality

* post messages up to 140 characters long
* list posted (own) messages in reverse order (from latest)
* list messages posted by all followed users
* follow another a user
* stop following a user

## Build it
```mvn clean package```

## Run it
```mvn spring-boot:run```
It will start the application on port 8080.

## API

* /api/posts - list of all published posts
```bash
curl http://localhost:8080/api/posts 

```
* POST /api/users/{name}/wall - post new message from user {name}
```bash
curl -X POST \
  http://localhost:8080/api/users/john/wall \
  -H 'content-type: application/json' \
  -d '{ "text": "Message from John"}'
```
* GET /api/users/{name}/wall - list of user's posts - from latest
```bash
curl http://localhost:8080/api/users/john/wall 
```
* GET /api/users/{name}/timeline - posts from followed users - from latest
```bash
curl http://localhost:8080/api/users/john/timeline 
```
