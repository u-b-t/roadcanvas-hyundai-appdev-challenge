package kr.roadcanvas.app.test

import io.cucumber.junit.CucumberOptions

@CucumberOptions(features = ["features"], glue = ["kr.roadcanvas.app.test"])
class CucumberTest
