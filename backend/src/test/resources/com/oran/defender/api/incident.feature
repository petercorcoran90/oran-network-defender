Feature: Incident API

Background:
  * url karate.properties['karate.baseUrl']

Scenario: list incidents for the seeded session
  Given path 'api', 'sessions', 1, 'incidents'
  When method get
  Then status 200
  And match response == '#[4]'
  And match response[*].incidentType contains 'CELL_OVERLOAD'
  And match response[*].status contains 'OPEN'

Scenario: filter open incidents for the seeded session
  Given path 'api', 'sessions', 1, 'incidents'
  And param status = 'OPEN'
  When method get
  Then status 200
  And match response == '#[4]'
  And match each response[*].status == 'OPEN'

Scenario: read a single incident without exposing the root cause
  Given path 'api', 'sessions', 1, 'incidents', 1
  When method get
  Then status 200
  And match response.id == 1
  And match response.incidentType == 'CELL_OVERLOAD'
  And match response.status == 'OPEN'
  And match response.rootCause == '#notpresent'
