Feature: Session API

Background:
  * url karate.properties['karate.baseUrl']
  * def UUID = Java.type('java.util.UUID')
  * def suffix = UUID.randomUUID().toString().replace('-', '')

Scenario: create a session and auto-start when two players join
  * def creatorName = 'karate_creator_' + suffix
  * def opponentName = 'karate_opponent_' + suffix

  Given path 'api', 'users'
  And request { username: '#(creatorName)', role: 'PLAYER' }
  When method post
  Then status 201
  * def creatorId = response.id

  Given path 'api', 'users'
  And request { username: '#(opponentName)', role: 'PLAYER' }
  When method post
  Then status 201
  * def opponentId = response.id

  Given path 'api', 'sessions'
  And request { name: 'Karate Match', createdByUserId: '#(creatorId)', durationSeconds: 120 }
  When method post
  Then status 201
  And match response.id == '#number'
  And match response.sessionCode == '#regex [A-Z2-9]{6}'
  And match response.status == 'WAITING'
  * def sessionId = response.id

  Given path 'api', 'sessions', sessionId, 'join'
  And request { userId: '#(creatorId)', teamName: 'Blue' }
  When method post
  Then status 201
  And match response.userId == creatorId
  And match response.username == creatorName
  And match response.teamName == 'Blue'
  And match response.score == 0

  Given path 'api', 'sessions', sessionId, 'join'
  And request { userId: '#(opponentId)', teamName: 'Red' }
  When method post
  Then status 201
  And match response.userId == opponentId
  And match response.username == opponentName
  And match response.teamName == 'Red'

  Given path 'api', 'sessions', sessionId
  When method get
  Then status 200
  And match response.status == 'ACTIVE'
  And match response.durationSeconds == 120

  Given path 'api', 'sessions', sessionId, 'players'
  When method get
  Then status 200
  And match response == '#[2]'

Scenario: reject starting a session before two players join
  * def creatorName = 'karate_single_' + suffix

  Given path 'api', 'users'
  And request { username: '#(creatorName)', role: 'PLAYER' }
  When method post
  Then status 201
  * def creatorId = response.id

  Given path 'api', 'sessions'
  And request { name: 'Karate Solo Match', createdByUserId: '#(creatorId)', durationSeconds: 120 }
  When method post
  Then status 201
  * def sessionId = response.id

  Given path 'api', 'sessions', sessionId, 'join'
  And request { userId: '#(creatorId)', teamName: 'Blue' }
  When method post
  Then status 201

  Given path 'api', 'sessions', sessionId, 'start'
  When method post
  Then status 409
  And match response.message == 'Session needs 2 players to start'
