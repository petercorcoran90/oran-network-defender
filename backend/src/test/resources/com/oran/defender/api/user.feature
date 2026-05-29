Feature: User API

Background:
  * url karate.properties['karate.baseUrl']
  * def UUID = Java.type('java.util.UUID')
  * def suffix = UUID.randomUUID().toString().replace('-', '')

Scenario: create and fetch a user
  * def username = 'karate_user_' + suffix

  Given path 'api', 'users'
  And request { username: '#(username)', role: 'PLAYER' }
  When method post
  Then status 201
  And match response.id == '#number'
  And match response.username == username
  And match response.role == 'PLAYER'
  * def userId = response.id

  Given path 'api', 'users', userId
  When method get
  Then status 200
  And match response.id == userId
  And match response.username == username
  And match response.role == 'PLAYER'

Scenario: reject a duplicate username
  * def username = 'karate_duplicate_' + suffix

  Given path 'api', 'users'
  And request { username: '#(username)', role: 'PLAYER' }
  When method post
  Then status 201

  Given path 'api', 'users'
  And request { username: '#(username)', role: 'PLAYER' }
  When method post
  Then status 409
  And match response.message == 'Username already taken'
