/**
 *  Sensibo Device Type Handler
 *
 *  Copyright 2021 Paul Hutton
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Date          Comments
 *  2021-02-15	  Forked from Bryan Li's port from ST	    
 *
 */

preferences {
    //Logging Message Config
    input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
    input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
    input name: "traceLogging", type: "bool", title: "Enable trace message logging", description: ""
}

metadata {
	definition (name: "SensiboPod", namespace: "joyfulhouse", author: "Bryan Li", oauth: false) {
            // capability "Actuator"
            capability "Battery"
            capability "Health Check"
            capability "Polling"
            capability "PowerSource"
            capability "Refresh"
            capability "RelativeHumidityMeasurement"
            capability "Sensor"
            capability "Switch"
            capability "TemperatureMeasurement"
            // capability "Thermostat"
            capability "Voltage Measurement"
        
            attribute "swing", "String"
            attribute "temperatureUnit","String"
            attribute "productModel","String"
            attribute "firmwareVersion","String"
            attribute "Climate","String"
            attribute "targetTemperature","Double"
            attribute "statusText","String"
            attribute "currentmode","String"
            attribute "fanLevel","String"
            //attribute "on","String"   // Added by request of NateG
        
        
            // command "setAll"
            // command "switchFanLevel"
            command "switchMode"
            //command "raiseCoolSetpoint"
            //command "lowerCoolSetpoint"
            //command "raiseHeatSetpoint"
            //command "lowerHeatSetpoint" 
            // command "voltage"
            command "raiseTemperature"
            command "lowerTemperature"
//            command "switchSwing"
//            command "modeSwing", [
//                [
//                    name:"Swing Mode", type: "ENUM", description: "Pick an option", constraints: [
//                        "fixedTop",
//                        "fixedMiddleTop",
//                        "fixedMiddle",
//                        "fixedMiddleBottom",
//                        "fixedBottom",
//                        "rangeTop",
//                        "rangeMiddle",
//                        "rangeBottom",
//                        "rangeFull",
//                        "horizontal",
//                        "both",
//                        "stopped"
//                    ] 
//                ]
//            ]
//            command "setThermostatMode"
            command "modeHeat"
            command "modeCool"
            command "modeDry"
            command "modeFan"
            command "modeAuto"
            command "lowFan"
            command "mediumFan"
            command "highFan"
            command "quietFan"
            command "strongFan"
//            command "fullswing"
            // command "setAirConditionerMode"
            command "toggleClimateReact"
            command "setCoolingSetpoint", ["number"]
            command "setHeatingSetpoint", ["number"]
            command "setClimateReact", [
                [
                    name:"State", type: "ENUM", constraints: [
                        "on",
                        "off"
                    ]
                ]
            ]
            // command "configureClimateReact"
	}
}

// determines the extent of the logging based upon device preferences
private def displayDebugLog(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}
private def displayTraceLog(message) {
	if (traceLogging) log.trace "${device.displayName}: ${message}"
}
private def displayInfoLog(message) {
	if (infoLogging || state.prefsSetCount != 1)
		log.info "${device.displayName}: ${message}"
}

// Logging and event management
def generatefanLevelEvent(mode) {
   sendEvent(name: "fanLevel", value: mode, descriptionText: "Fan mode is now ${mode}")
}

def generateModeEvent(mode) {
   sendEvent(name: "Mode", value: mode, descriptionText: "AC mode is now ${mode}")   
}

def generateStatusEvent() {
    def temperature = device.currentValue("temperature").toDouble()  
    def humidity = device.currentValue("humidity").toDouble() 
    def targetTemperature = device.currentValue("targetTemperature").split(' ')[0].toDouble()
    def fanLevel = device.currentValue("fanLevel")
    def mode = device.currentValue("currentmode")
    // def on = device.currentValue("on")
    def swing = device.currentValue("swing")
    def ClimateReact = device.currentValue("Climate")
    def error = device.currentValue("Error")
    def sUnit = device.currentValue("temperatureUnit")                
    def statusTextmsg = ""
    
    statusTextmsg = "${humidity}%"
   
    // sendEvent("name":"statusText", "value":statusTextmsg, "description":"")
}

def debugEvent(value, message) {

	def results = [
            name: "devdebug",
            value: value,
            descriptionText: message,
	]
	displayDebugLog( "Generating DevDebug Event: ${results}")
	sendEvent (results)
}

def generateErrorEvent() {
   displayDebugLog("$device.displayName FAILED to set the AC State")
   sendEvent(name: "Error", value: "Error", descriptionText: "$device.displayName FAILED to set or get the AC State", isStateChange: true)  
}

def generateSetTempEvent(temp) {
   sendEvent(name: "targetTemperature", value: temp, descriptionText: "$device.displayName set temperature is now ${temp}", isStateChange: true)
}

def generateSwitchEvent(mode) {
   // sendEvent(name: "on", value: mode, descriptionText: "$device.displayName is now ${mode}", displayed: false, isStateChange: true)  
   sendEvent(name: "switch", value: mode, descriptionText: "$device.displayName is now ${mode}", isStateChange: true)   
   
   //refresh()
}

// Unit conversion
def fToc(temp) {
	return ((temp - 32) / 1.8).toDouble()
}

// AC Control functions styart here
def switchMode() {
	displayTraceLog( "switchMode() called")
    
	def currentMode = device.currentState("currentmode")?.value
	displayDebugLog("switching AC mode from current mode: $currentMode")
	def returnCommand

	switch (currentMode) {
		case "heat":
			returnCommand = modeMode("cool")
			break
		case "cool":
			returnCommand = modeMode("fan")
			break		
		case "fan":
			returnCommand = modeMode("dry")
			break
        case "dry":
            returnCommand = modeMode("auto")
			break
        case "auto":
			returnCommand = modeMode("heat")
			break
	}

	returnCommand
}

def modeMode(String newMode){
    displayTraceLog( "modeMode() called with " + newMode)
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
        
    def LevelBefore = device.currentState("fanLevel").value
    def capabilities = parent.getCapabilities(device.deviceNetworkId,newMode)
    def Level = LevelBefore
    if (capabilities.remoteCapabilities != null) {
    	def fanLevels = capabilities.remoteCapabilities.fanLevels
    	
        displayDebugLog("Fan levels capabilities : " + capabilities.remoteCapabilities.fanLevels)
        
        Level = GetNextFanLevel(LevelBefore,capabilities.remoteCapabilities.fanLevels)
        
        //displayDebugLog("Fan : " + Level)
   
        def result = parent.setACStates(this, device.deviceNetworkId, "on", newMode, Setpoint, Level, device.currentState("swing").value, device.currentState("temperatureUnit").value)
        if (result) {
            displayInfoLog( "Mode changed to " + newMode + " for " + device.deviceNetworkId)
            
            if (LevelBefore != Level) {
                generatefanLevelEvent(Level)
            }
            sendEvent(name: 'thermostatMode', value: newMode,isStateChange: true)
            displayDebugLog("Current state: ${device.currentState("switch")}.value")
            if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }

            generateModeEvent(newMode)
            // generateStatusEvent()
            refresh()
        }
        else {
            generateErrorEvent()
            // generateStatusEvent()
        }             
    }
    else {
        def themodes = parent.getCapabilities(device.deviceNetworkId,"modes")
        def sMode = GetNextMode(newMode,themodes)

        NextMode(sMode)
    }
}

def GetNextFanLevel(fanLevel, fanLevels) {
    displayTraceLog( "GetNextFanLevel called with " + fanLevel)
    
    if (fanLevels == null || fanLevels == "null") {
      return null
    }
    
    def listFanLevel = ['low','medium','high','auto','quiet','medium_high','medium_low','strong']	
    def newFanLevel = returnNext(listFanLevel,fanLevels,fanLevel)
    
    displayDebugLog("Next fanLevel = " + newFanLevel)
	
    return newFanLevel
}

def returnNext(liste1, liste2, val) throws Exception
{
    try {
    	def index = liste2.indexOf(val)
        
        if (index == -1) throw new Exception()
        else return liste2[liste2.indexOf(val)]
    }
    catch(Exception e) {
    	if (liste1.indexOf(val)+ 1 == liste1.size()) {
           val = liste1[0]
           }
         else {
           val = liste1[liste1.indexOf(val) + 1]
         }
         returnNext(liste1, liste2, val)
    }	
}

def refresh() {
  displayTraceLog( "refresh() called")
  poll()
  displayTraceLog( "refresh() ended")
}

void poll() {
    displayTraceLog( "Executing 'poll' using parent SmartApp")
	
    def results = parent.pollChild(this)
    def linkText = getLinkText(device)
                    
    parseTempUnitEventData(results)
    parseEventData(results)
}

def parseTempUnitEventData(Map results) {
    displayDebugLog("parsing data $results")
    
    if(results) {
        results.each { name, value ->
            if (name=="temperatureUnit") { 
                def linkText = getLinkText(device)
                def isChange = true

                sendEvent(
                    name: name,
                    value: value,
                    //unit: value,
                    descriptionText: "${name} = ${value}",
                    handlerName: "temperatureUnit",
                    isStateChange: isChange,)
            }
        }
    }
}

def parseEventData(Map results) {
    displayDebugLog("parsing Event data $results")
    
    if(results) {
        results.each { name, value -> 
 
            displayDebugLog("name :" + name + " value :" + value)
            
            def linkText = getLinkText(device)
            def isChange = false
                             
            if (name=="voltage") {
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                                  
                sendEvent(
                    name: name,
                    value: value,
                    unit: "mA",
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name== "battery") {            	                
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                 
                sendEvent(
                    name: name,
                    value: value,
                    //unit: "V",
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name== "powerSource") {            	                
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                  
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name== "Climate") {            	                
                isChange = true
                  
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name=="on") {            	
                isChange = true
                   
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
                    
                sendEvent(name: "switch", value: value)
            }
            /*else if (name=="thermostatMode") {
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                 
                if (value=="cool") {
                    sendEvent(name: 'airConditionerMode', value: "cool", 
                        isStateChange: isChange)
					
                    sendEvent(name: 'thermostatOperatingState', value: "cooling", 
                        isStateChange: isChange)
                } 
                else if (value=="heat") {
                    sendEvent(name: 'airConditionerMode', value: "heat", 
                        isStateChange: isChange)
                    
                    sendEvent(name: 'thermostatOperatingState', value: "heating", 
                        isStateChange: isChange)
                } 
                else if (value=="fan") {
                    sendEvent(name: 'airConditionerMode', value: "fanOnly", 
                        isStateChange: isChange)
                
                    sendEvent(name: 'thermostatOperatingState', value: "fan only", 
                        isStateChange: isChange)
                } 
                else if (value=="dry") {
                    sendEvent(name: 'airConditionerMode', value: "dry", 
                        isStateChange: isChange)
                    
                    sendEvent(name: 'thermostatOperatingState', value: "dry", 
                        isStateChange: isChange)
                 } 
                 else if (value=="auto") {
                    sendEvent(name: 'airConditionerMode', value: "auto", 
                        isStateChange: isChange)
                    
                    sendEvent(name: 'thermostatOperatingState', value: "auto", 
                        isStateChange: isChange)
                } 
                else {
                    sendEvent(name: 'thermostatOperatingState', value: "idle", 
                        isStateChange: isChange)
                }      
				
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name=="coolingSetpoint" || name== "heatingSetpoint" || name == "thermostatSetpoint") {           	
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                
                sendEvent(
                    name: name,
                    value: value,
                    //unit : device.currentValue("temperatureUnit"),
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }*/
            else if (name=="temperatureUnit") { 
                isChange = true
                   
                sendEvent(
                    name: name,
                    value: value,
                    //unit: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            /*else if (name=="thermostatFanMode") {
            	def mode = (value.toString() == "high" || value.toString() == "medium") ? "on" : value.toString()
                mode = (mode == "low") ? "circulate" : mode
               	
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                   
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }*/
            else if (name=="swing") {
              	
                isChange = true //isTemperatureStateChange(device, name, value.toString())
                   
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
            }
            else if (name=="temperature" || name== "lastTemperaturePush" || name== "lastHumidityPush") {
                isChange = true //isTemperatureStateChange(device, name, value.toString())
				
                sendEvent(
                    name: name,
                    value: value,
                    //unit: device.currentValue("temperatureUnit"),
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
                    
            }
            else if (name=="humidity") {
                isChange = true //isTemperatureStateChange(device, name, value.toString())
				
                sendEvent(
                    name: name,
                    value: value,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)                    
            }
            else {
            	isChange = true//isStateChange(device, name, value.toString())
                
                sendEvent(
                    name: name,
                    value: value.toString(),
                    // linkText: linkText,
                    descriptionText: getThermostatDescriptionText(name, value, linkText),
                    handlerName: name,
                    isStateChange: isChange)
                    
            }
        }          

        // generateStatusEvent ()
    }
}

void raiseTemperature() {
    displayTraceLog( "raiseTemperature() called"	)

    def operMode = device.currentState("currentmode").value
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")
    
    displayDebugLog("Current target temperature = ${Setpoint}")

    Setpoint = temperatureUp(Setpoint)
    
    if (Setpoint == -1) { 
      return
    }
    
    switch (operMode) {
    	case "heat":
            setHeatingSetpoint(Setpoint)
            break;
        case "cool":
            setCoolingSetpoint(Setpoint)
            break;
        case "fan":
            setFanSetpoint(Setpoint)
            break;
         case "dry":
            setDrySetpoint(Setpoint)
            break;
        case "auto":
            setHeatingSetpoint(Setpoint)
            setCoolingSetpoint(Setpoint)
            break;
        default:
        	break;
    }
}

def temperatureUp(temp) {
    displayTraceLog( "temperatureUp() called with "+ temp)
    
    def sunit = device.currentValue("temperatureUnit")
    def capabilities = parent.getCapabilities(device.deviceNetworkId, device.currentState("currentmode").value)
    def values
    
    if (sunit == "F") {
    	if (capabilities.remoteCapabilities.temperatures.F == null) {
            return -1
    	}
        values = capabilities.remoteCapabilities.temperatures.F.values                
    }
    else {
    	if (capabilities.remoteCapabilities.temperatures.C == null) {
            return -1
    	}
    	values = capabilities.remoteCapabilities.temperatures.C.values
    }
    
    def found = values.findAll{number -> number > temp}

    displayDebugLog("Values retrieved : " + found)
    
    if (found == null || found.empty) found = values.last()
    else found = found.first()

    displayDebugLog("Temp before : " + temp    )          
    displayDebugLog("Temp after : " + found)

    temp = found
        
    return temp
}

// Set Temperature
def setHeatingSetpoint(temp) {
    displayTraceLog( "setHeatingSetpoint() called")

    temp = temp.toInteger()
    displayDebugLog("setTemperature : " + temp)
    
    def result = parent.setACStates(this, device.deviceNetworkId , "on", "heat", temp, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    
    if (result) {
    	displayInfoLog( "Heating temperature changed to " + temp + " for " + device.deviceNetworkId)
        
        if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
    	generateModeEvent("heat")
    	//sendEvent(name: 'coolingSetpoint', value: temp, displayed: false)
    	sendEvent(name: 'heatingSetpoint', value: temp, displayed: false)
        sendEvent(name: 'thermostatSetpoint', value: temp, displayed: false)
    	generateSetTempEvent(temp)
        
        // generateStatusEvent()
    	refresh()
    }	
    else {
       	generateErrorEvent()
        // generateStatusEvent()
    }    
}

// Set Temperature
def setCoolingSetpoint(temp) {
    displayTraceLog( "setCoolingSetpoint() called")

    temp = temp.toInteger()
    displayDebugLog("setTemperature : " + temp  ) 
    
    def result = parent.setACStates(this, device.deviceNetworkId , "on", "cool", temp, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    
    if (result) {
    	displayInfoLog( "Cooling temperature changed to " + temp + " for " + device.deviceNetworkId)
        
    	if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        generateModeEvent("cool")
         
    	sendEvent(name: 'coolingSetpoint', value: temp, displayed: false)
        //sendEvent(name: 'thermostatSetpoint', value: temp, displayed: false)
    	//sendEvent(name: 'heatingSetpoint', value: temp, displayed: false)
    	generateSetTempEvent(temp)
        
        // generateStatusEvent()
    	refresh()
    }
    else {
       	generateErrorEvent()
        // generateStatusEvent()
    }
}

// Set Temperature
def setFanSetpoint(temp) {
    displayTraceLog( "setFanSetpoint() called")

    temp = temp.toInteger()
    displayDebugLog("setTemperature : " + temp  ) 
    
    def result = parent.setACStates(this, device.deviceNetworkId , "on", "fan", temp, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    
    if (result) {
    	displayInfoLog( "Fan temperature changed to " + temp + " for " + device.deviceNetworkId)
        
    	if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        generateModeEvent("fan")
         
        sendEvent(name: 'thermostatSetpoint', value: temp, displayed: false)
    	generateSetTempEvent(temp)
        
        // generateStatusEvent()
    	refresh()
    }
    else {
       	generateErrorEvent()
        // generateStatusEvent()
    }
}

// Set Temperature
def setDrySetpoint(temp) {
    displayTraceLog( "setDrySetpoint() called")

    temp = temp.toInteger()
    displayDebugLog("setTemperature : " + temp  ) 
    
    def result = parent.setACStates(this, device.deviceNetworkId , "on", "dry", temp, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    
    if (result) {
    	displayInfoLog( "Dry temperature changed to " + temp + " for " + device.deviceNetworkId)
        
    	if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        generateModeEvent("dry")
         
        sendEvent(name: 'thermostatSetpoint', value: temp, displayed: false)
    	generateSetTempEvent(temp)
        
        // generateStatusEvent()
    	refresh()
    }
    else {
       	generateErrorEvent()
        // generateStatusEvent()
    }   
}

void lowerTemperature() {
    displayTraceLog( "lowerTemperature() called")
    
    def operMode = device.currentState("currentmode").value
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")
    
    displayDebugLog("Current target temperature = ${Setpoint}")

    Setpoint = temperatureDown(Setpoint)
    
    if (Setpoint == -1) { 
      return
    }
    
    switch (operMode) {
    	case "heat":
            setHeatingSetpoint(Setpoint)
            break;
        case "cool":
            setCoolingSetpoint(Setpoint)
            break;
        case "fan":
            setFanSetpoint(Setpoint)
            break;
         case "dry":
            setDrySetpoint(Setpoint)
            break;
        case "auto":
            setHeatingSetpoint(Setpoint)
            setCoolingSetpoint(Setpoint)
            break;
        default:
        	break;
    }
}

def modeHeat() {
    displayTraceLog( "modeHeat() called")
    modeMode("heat")
}

def modeCool() {
    displayTraceLog( "modeCool() called")
    modeMode("cool")
}

def modeDry() {
    displayTraceLog( "modeDry() called")
    modeMode("dry")
}

def modeFan() {
    displayTraceLog( "modeFan() called")
    modeMode("fan")
}

def modeAuto() {
    displayTraceLog( "modeAuto() called")
    modeMode("auto")
}

def dfanLevel(String newLevel){
    displayTraceLog( "dfanLevel called with fan = " + newLevel)
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    
    def capabilities = parent.getCapabilities(device.deviceNetworkId, device.currentState("currentmode").value)      
    def Level = LevelBefore
    if (capabilities.remoteCapabilities != null) {
    	def fanLevels = capabilities.remoteCapabilities.fanLevels
        
    	displayDebugLog("Fan levels capabilities : " + capabilities.remoteCapabilities.fanLevels)
        
        Level = GetNextFanLevel(newLevel,capabilities.remoteCapabilities.fanLevels)
        //displayDebugLog("Fan : " + Level)
        
        def result = parent.setACStates(this, device.deviceNetworkId,"on", device.currentState("currentmode").value, Setpoint, Level, device.currentState("swing").value, device.currentState("temperatureUnit").value)

        if (result) {
            displayInfoLog( "Fan level changed to " + Level + " for " + device.deviceNetworkId)
            
            if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
            if (Level == "low") {
            	sendEvent(name: 'thermostatFanMode', value: "circulate", displayed: false)
            }
            else {            	
                sendEvent(name: 'thermostatFanMode', value: "on", displayed: false)
            }
            generatefanLevelEvent(Level)
            
            // generateStatusEvent()
            refresh()
        }
        else {
            generateErrorEvent()
            // generateStatusEvent()
        }             
    }
    else {
    	displayDebugLog ("Fan mode does not exist")
        // other instructions may be required if mode does not exist
    }    
}

def lowFan() {
    displayTraceLog( "lowfan() called")
    dfanLevel("low")
}

def mediumFan() {
    displayTraceLog( "mediumfan() called")
    dfanLevel("medium")
}

def highFan() {
    displayTraceLog( "highfan() called")
    dfanLevel("high")
}

def quietFan() {
    displayTraceLog( "quietfan() called")
    dfanLevel("quiet")
}

def strongFan() {
    displayTraceLog( "strongfan() called")
    dfanLevel("strong")
}

def autoFan() {
    displayTraceLog( "autofan() called")
    dfanLevel("auto")
}

def setAll(newMode,temp,fan) {
    displayTraceLog( "setAll() called with " + newMode + "," + temp + "," + fan )
    
    def Setpoint = temp.toInteger()
    def LevelBefore = fan
    def capabilities = parent.getCapabilities(device.deviceNetworkId,newMode)
    def Level = LevelBefore
    if (capabilities.remoteCapabilities != null) {
    	def fanLevels = capabilities.remoteCapabilities.fanLevels
        
    	displayDebugLog("Fan levels capabilities : " + capabilities.remoteCapabilities.fanLevels)
        
        Level = GetNextFanLevel(LevelBefore,capabilities.remoteCapabilities.fanLevels)
        displayDebugLog("Fan : " + Level)
   
        def result = parent.setACStates(this, device.deviceNetworkId, "on", newMode, Setpoint, Level, device.currentState("swing").value, device.currentState("temperatureUnit").value)
       
        if (result) {
            if (LevelBefore != Level) {
                generatefanLevelEvent(Level)
            }
            sendEvent(name: 'thermostatMode', value: newMode, displayed: false,isStateChange: true)
            if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }

            generateModeEvent(newMode)
            // generateStatusEvent()
            refresh()
        }
        else {
            generateErrorEvent()
            // generateStatusEvent()
        }              
    }
    else {       
    }
}

def fullswing()
{	
	displayTraceLog( "fullswing() called")
	modeSwing("rangeFull")
}

def temperatureDown(temp)
{
	displayTraceLog( "temperatureDown() called with "+ temp)
    
	def sunit = device.currentValue("temperatureUnit")
    def capabilities = parent.getCapabilities(device.deviceNetworkId, device.currentState("currentmode").value)
    def values
       
    if (sunit == "F") {
    	if (capabilities.remoteCapabilities.temperatures.F == null) {
       		return -1
    	}
    	values = capabilities.remoteCapabilities.temperatures.F.values                
    }
    else {
    	if (capabilities.remoteCapabilities.temperatures.C == null) {
       		return -1
    	}
    	values = capabilities.remoteCapabilities.temperatures.C.values
    }
    
    def found = values.findAll{number -> number < temp}
       
	displayDebugLog("Values retrieved : " + found)
    
    if (found == null || found.empty) found = values.first()
    else found = found.last()
        
    displayDebugLog("Temp before : " + temp   )            
    displayDebugLog("Temp after : " + found)
        
    temp = found
        
    return temp
}

void lowerCoolSetpoint() {
    displayTraceLog( "lowerCoolSetpoint() called")
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")

    displayDebugLog("Current target temperature = ${Setpoint}")
	
    Setpoint = temperatureDown(Setpoint)

    def result = parent.setACStates(this, device.deviceNetworkId , "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)

    if (result) {
    	displayInfoLog( "Cooling temperature changed to " + Setpoint + " for " + device.deviceNetworkId)
        
        if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        
        sendEvent(name: 'coolingSetpoint', value: Setpoint,  displayed: false)
        //sendEvent(name: 'thermostatSetpoint', value: Setpoint,  displayed: false)
       
        generateSetTempEvent(Setpoint)
        
    	displayDebugLog("New target Temperature = ${Setpoint}")
        
        //generateStatusEvent()
    	refresh()
    }
	else {
    	displayDebugLog("error")
       	generateErrorEvent()
        
        //generateStatusEvent()
    }	
}

void setThermostatMode(modes) { 
    displayTraceLog( "setThermostatMode() called")

    def currentMode = device.currentState("currentmode").value

    displayDebugLog("switching AC mode from current mode: $currentMode")

    switch (modes) {
        case "cool":
            modeCool()
            break
        //case "fan":
        //	returnCommand = modeFan()
        //	break		
        //case "dry":
        //	returnCommand = modeDry()
        //	break
    case "auto":
        modeAuto()
        break
    case "heat":
        modeHeat()
        break
    case "off":
        off()
        break
    }
}

void setAirConditionerMode(modes)
{ 
	displayTraceLog( "setAirConditionerMode() called")
    
  	def currentMode = device.currentState("currentmode").value
  
  	displayDebugLog("switching AC mode from current mode: $currentMode")

  	switch (modes) {
		case "cool":
			modeCool()
			break
		case "fanOnly":
        case "fan":
			modeFan()
			break		
		case "dry":
			modeDry()
			break
        case "auto":
	        modeAuto()
			break
        case "heat":
			modeHeat()
			break
	}
}

void raiseCoolSetpoint() {
   	displayTraceLog( "raiseCoolSetpoint() called")
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")
    
    displayDebugLog("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

    def result = parent.setACStates(this, device.deviceNetworkId , "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    if (result) {
    	displayInfoLog( "Cooling temperature changed to " + Setpoint + " for " + device.deviceNetworkId)
        
        if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        
        sendEvent(name: 'coolingSetpoint', value: Setpoint, displayed: false)
        sendEvent(name: 'thermostatSetpoint', value: Setpoint, displayed: false)
        
        generateSetTempEvent(Setpoint)
        
    	displayDebugLog("New target Temperature = ${Setpoint}")
        
        generateStatusEvent()
    	refresh()
    }
	else {
       	generateErrorEvent()
        
        generateStatusEvent()
    }	
}

void raiseHeatSetpoint() {
    displayTraceLog( "raiseHeatSetpoint() called")
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")
    
    displayDebugLog("Current target temperature = ${Setpoint}")

    Setpoint = temperatureUp(Setpoint)

    def result = parent.setACStates(this, device.deviceNetworkId , "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    if (result) {
    	displayInfoLog( "Heating temperature changed to " + Setpoint + " for " + device.deviceNetworkId)
        
        if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        
        sendEvent(name: 'heatingSetpoint', value: Setpoint, displayed: false)
        //sendEvent(name: 'thermostatSetpoint', value: Setpoint, displayed: false)
        
        generateSetTempEvent(Setpoint)
        
    	displayDebugLog("New target Temperature = ${Setpoint}")
        
        //generateStatusEvent()
    	refresh()
    }
	else {
            generateErrorEvent()
        
        //generateStatusEvent()
    }
	
}

void lowerHeatSetpoint() {
    displayTraceLog( "lowerHeatSetpoint() called")
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
    def theTemp = device.currentValue("temperatureUnit")
    
    displayDebugLog("Current target temperature = ${Setpoint}")

    Setpoint = temperatureDown(Setpoint)

    def result = parent.setACStates(this, device.deviceNetworkId , "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    if (result) {
    	displayInfoLog( "Heating temperature changed to " + Setpoint + " for " + device.deviceNetworkId)
        
        if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
        
        sendEvent(name: 'heatingSetpoint', value: Setpoint, displayed: false)    
        //sendEvent(name: 'thermostatSetpoint', value: Setpoint, displayed: false)
        
        generateSetTempEvent(Setpoint)
        
    	displayDebugLog("New target Temperature = ${Setpoint}")
        
        //generateStatusEvent()
    	refresh()
    }
	else {
       	generateErrorEvent()
        
        //generateStatusEvent()
    }	
}

// Turn off or Turn on the AC
def on() {
    displayTraceLog( "on called")
    
    //def Setpoint = device.currentValue("targetTemperature").toInteger()
    def Setpoint = device.currentValue("targetTemperature")
   
    displayDebugLog("Temp Unit : " + device.currentState("temperatureUnit").value)
    displayDebugLog("Temp Unit (Setpoint) : " + Setpoint)
    def result = parent.setACStates(this, device.deviceNetworkId, "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    displayDebugLog("Result : " + result)
    if (result) {
    	displayInfoLog( "AC turned ON for " + device.deviceNetworkId)
    	sendEvent(name: 'thermostatMode', value: device.currentState("currentmode").value,isStateChange: true)
        //sendEvent(name: 'thermostatOperatingState', value: "idle",isStateChange: true)
    	
        generateSwitchEvent("on")
        
        // generateStatusEvent() 
    	refresh()
    }
    else {
       	generateErrorEvent()
        // generateStatusEvent() 
    }        
}

def off() {
    displayTraceLog( "off called")
    
    //def Setpoint = device.currentValue("targetTemperature").toInteger()
    def Setpoint = device.currentValue("targetTemperature")
   
    displayDebugLog("Temp Unit : " + device.currentState("temperatureUnit").value)
    displayDebugLog("Temp Unit (Setpoint) : " + Setpoint)
    
    def result = parent.setACStates(this, device.deviceNetworkId, "off", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, device.currentState("swing").value, device.currentState("temperatureUnit").value)
    
    if (result) {
    	displayInfoLog( "AC turned OFF for " + device.deviceNetworkId)
        
    	sendEvent(name: 'thermostatMode', value: "off", isStateChange: true)
        //sendEvent(name: 'thermostatOperatingState', value: "idle",isStateChange: true)
    	
        generateSwitchEvent("off")
        
        // generateStatusEvent()
    	refresh()
     }
    else {
       	generateErrorEvent()
        // generateStatusEvent()
    }         
}

// toggle Climate React
def toggleClimateReact()
{
  	displayTraceLog( "toggleClimateReact() called")
    
	def currentClimateMode = device.currentState("Climate")?.value
    
    def returnCommand
    
    switch (currentClimateMode) {
    	case "off":
        	returnCommand = setClimateReact("on")
            break
        case "on":
        	returnCommand = setClimateReact("off")
            break            
    }
    
    if (!returnCommand) { returnCommand }
}

// Set Climate React
def setClimateReact(ClimateState) {

    ///////////////////////////////////////////////
    /// Parameter ClimateState : "on" or "off"
    ///////////////////////////////////////////////
    
	displayTraceLog( "setClimateReact() called")
    
	displayDebugLog("Climate : " + ClimateState   )
   
    def result = parent.setClimateReact(this, device.deviceNetworkId, ClimateState)
    
    if (result) {
    	displayInfoLog( "Climate React changed to " + ClimateState + " for " + device.deviceNetworkId)
              
        sendEvent(name: 'Climate', value: ClimateState, displayed: false)
    	//generateSetTempEvent(temp)
        
        // generateStatusEvent()
    	refresh()
    }
    else {
       	generateErrorEvent()
        //generateStatusEvent()
        //refresh()
    }
}

/* // Disabling this for the time being
def configureClimateReact(lowThres, highThres,stype,lowState,highState, on_off, ThresUnit)
{
    ///////////////////////////////////////////////
    // lowThres and highThres - Integer parameters
	// stype : possible values are "temperature", "humidity" or "feelsLike"
    // lowState and highState : 
    //    on, fanLevel, temperatureUnit, targetTemperature, mode      
    //
    //    like  "[true,'auto','C',21,'heat']"
    //    to turn off AC,first parameters = false : "[false,'auto','C',21,'heat']"
    // one_off : boolean value to enable/disable the Climate React
    // unit : Passing F for Farenheit or C for Celcius
    // 
    // Some examples: 
    //  
    // Range 19-24 Celcius, start to heat to 22 at auto fan if the temp is lower than 19 and stop the AC when higher than 24
    // configureClimateReact(19, 24, ‘temperature’, ‘[true, ‘auto’, ‘C’, 22, ‘heat’]’, ‘[false, ‘auto’, ‘C’, 22, ‘heat’]’, true, ‘C’);
    //
    // Range 67-68 Farenheit, start to heat to 68 at auto fan if the temp is lower than 67 and stop the AC when higher than 68
    // configureClimateReact(67, 68, ‘temperature’, ‘[true, ‘auto’, ‘F’, 68, ‘heat’]’, ‘[false, ‘auto’, ‘F’, 68, ‘heat’]’, true, ‘F’);
    //
    ///////////////////////////////////////////////
    
	displayTraceLog( "configureClimateReact() called")
    
    
    if (ThresUnit.toUpperCase() == "F")
    {
    	lowThres = fToc(lowThres).round(1)
    	highThres = fToc(highThres).round(1)
    }
    
    def json = new groovy.json.JsonBuilder()
    
    def lowStateMap = evaluate(lowState)
    def highStateMap = evaluate(highState)
        
    def lowStateJson
    def highStateJson
    
    if (lowStateMap) {
        lowStateJson = json {
            on lowStateMap[0]
            fanLevel lowStateMap[1]
            temperatureUnit lowStateMap[2]
            targetTemperature lowStateMap[3]
            mode lowStateMap[4]
        }
    }
    else { lowStateJson = null }
    
    if (highStateMap) {
        highStateJson = json {
            on highStateMap[0]
            fanLevel highStateMap[1]
            temperatureUnit highStateMap[2]
            targetTemperature highStateMap[3]
            mode highStateMap[4]
        }
    }
    else { highStateJson = null }
    
    def root = json {
    	deviceUid device.deviceNetworkId
        highTemperatureWebhook null
        highTemperatureThreshold highThres        
        lowTemperatureWebhook null
        type stype        
        lowTemperatureState lowStateJson
        enabled on_off
        highTemperatureState highStateJson
        lowTemperatureThreshold lowThres             
    }
    
    displayDebugLog("CLIMATE REACT STRING : " + JsonOutput.prettyPrint(json.toString()))
    def result = parent.configureClimateReact(this, device.deviceNetworkId, json.toString())
    
    if (result) {
    	displayInfoLog( "Climate React settings changed for " + device.deviceNetworkId)
              
        sendEvent(name: 'Climate', value: on_off, displayed: false)
        
        generateStatusEvent()
    	refresh()
    }
    else {
       	generateErrorEvent()
        
        generateStatusEvent()
    }
}*/

def switchFanLevel() {
	displayTraceLog( "switchFanLevel() called")
    
	def currentFanMode = device.currentState("fanLevel")?.value
	displayDebugLog("switching fan level from current mode: $currentFanMode")
	def returnCommand

	switch (currentFanMode) {
		case "low":
			returnCommand = dfanLevel("medium")
			break
		case "medium":
			returnCommand = dfanLevel("high")
			break
		case "high":
			returnCommand = dfanLevel("auto")
			break
        case "auto":
			returnCommand = dfanLevel("quiet")
			break
        case "quiet":
			returnCommand = dfanLevel("medium_high")
			break
         case "medium_high":
			returnCommand = dfanLevel("medium_low")
			break    
         case "medium_low":
			returnCommand = dfanLevel("strong")
			break
          case "strong":
			returnCommand = dfanLevel("low")
			break
	}

	returnCommand
}

def GetNextMode(mode, modes) {
    displayTraceLog( "GetNextMode called with " + mode)
        
    def listMode = ['heat','cool','fan','dry','auto']	
    def newMode = returnNext(listMode, modes,mode)
    
    displayDebugLog("Next Mode = " + newMode)
    
    return newMode
}

def NextMode(sMode) {
    displayTraceLog( "NextMode called()")

    if (sMode != null) {
        switch (sMode) {
            case "heat":
                modeHeat()
                break
            case "cool":
                modeCool()
                break
            case "fan":
                modeFan()
                break
            case "dry":
                modeDry()
                break
            case "auto":
                modeAuto()
                break                
        }
    }
    else 
    {
    	return null
    }
}

def GetNextSwingMode(swingMode, swingModes){
    displayTraceLog( "GetNextSwingMode() called with " + swingMode)
	
    if (swingModes == null || swingModes == "null") {
    	return null
    }
    
    def listSwingMode = ['stopped','fixedTop','fixedMiddleTop','fixedMiddle','fixedMiddleBottom','fixedBottom','rangeTop','rangeMiddle','rangeBottom','rangeFull','horizontal','both']	
    def newSwingMode = returnNext(listSwingMode, swingModes,swingMode)
    
    displayDebugLog("Next Swing Mode = " + newSwingMode)
    
    return newSwingMode
}

def switchSwing() {
    displayTraceLog( "switchSwing() called")

    def currentMode = device.currentState("swing")?.value
    displayDebugLog("switching Swing mode from current mode: $currentMode")
    def returnCommand
    switch (currentMode) {
        case "stopped":
            returnCommand = modeSwing("fixedTop")
            break
        case "fixedTop":
            returnCommand = modeSwing("fixedMiddleTop")
            break
        case "fixedMiddleTop":
            returnCommand = modeSwing("fixedMiddle")
            break
        case "fixedMiddle":
            returnCommand = modeSwing("fixedMiddleBottom")
            break
        case "fixedMiddleBottom":
            returnCommand = modeSwing("fixedBottom")
            break
        case "fixedBottom":
            returnCommand = modeSwing("rangeTop")
            break
        case "rangeTop":
            returnCommand = modeSwing("rangeMiddle")
            break        
        case "rangeMiddle":
            returnCommand = modeSwing("rangeBottom")
            break
        case "rangeBottom":
            returnCommand = modeSwing("rangeFull")
            break
        case "rangeFull":
            returnCommand = modeSwing("horizontal")
            break
        case "horizontal":
            returnCommand = modeSwing("both")
            break
        case "both":
            returnCommand = modeSwing("stopped")
            break
    }

    returnCommand
}
def modeSwing(String newSwing) {
    displayTraceLog( "modeSwing() called with " + newSwing)
    
    def Setpoint = device.currentValue("targetTemperature").toInteger()
   
    def SwingBefore = device.currentState("swing").value
    def capabilities = parent.getCapabilities(device.deviceNetworkId, device.currentState("currentmode").value)
    def Swing = SwingBefore
    if (capabilities.remoteCapabilities != null) {
    	def Swings = capabilities.remoteCapabilities.swing

        displayDebugLog("Swing capabilities : " + capabilities.remoteCapabilities.swing)

        Swing = GetNextSwingMode(newSwing,capabilities.remoteCapabilities.swing)
        //displayDebugLog("Swing : " + Swing)
        
        def result = parent.setACStates(this, device.deviceNetworkId, "on", device.currentState("currentmode").value, Setpoint, device.currentState("fanLevel").value, Swing, device.currentState("temperatureUnit").value)
        if (result) {
            displayInfoLog( "Swing mode changed to " + Swing + " for " + device.deviceNetworkId)
            
            sendEvent(name: 'swing', value: Swing, displayed: false,isStateChange: true)
            if (device.currentState("switch").value == "off") { generateSwitchEvent("on") }
            sendEvent(name: 'thermostatFanMode', value: "on", displayed: false)
            generateSwingModeEvent(Swing)
            
            // generateStatusEvent()
            refresh()
        }
        else {
            generateErrorEvent()
            // generateStatusEvent()
        }              
    }
    else {
      //TODO
    }
}

def generateSwingModeEvent(mode) {
   sendEvent(name: "swing", value: mode, descriptionText: "$device.displayName swing mode is now ${mode}", displayed: true, isStateChange: true)
}

private getThermostatDescriptionText(name, value, linkText) {
    if(name == "temperature") {
        return "$name was $value " + device.currentState("temperatureUnit").value
    }
    else if(name == "humidity") {
        return "$name was $value %"
    }
    else if(name == "targetTemperature") {
        return "latest temperature setpoint was $value " + device.currentState("temperatureUnit").value
    }
    else if(name == "fanLevel") {
        return "latest fan level was $value"
    }
    else if(name == "on") {
        return "latest switch was $value"
    }
    else if (name == "mode") {
        return "thermostat mode was ${value}"
    }
    else if (name == "currentmode") {
        return "thermostat mode was ${value}"
    }
    else if (name == "powerSource") {
        return "power source mode was ${value}"
    }
    else if (name == "Climate") {
        return "Climate React was ${value}"
    }
    else if (name == "thermostatMode")  {
        return "thermostat mode was ${value}"
    }
    else if (name == "temperatureUnit") {
    	return "thermostat unit was ${value}"
    }
    else if (name == "voltage") {
    	return "Battery voltage was ${value}"
    }
    else if (name == "battery") {
    	return "Battery was ${value}"
    }
    else if (name == "voltage" || name== "battery") {
    	return "Battery voltage was ${value}"
    }
    else if (name == "swing") {
    	return "Swing mode was ${value}"
    }
    else if (name == "Error") {
    	def str = (value == "Failed") ? "failed" : "success"
        return "Last setACState was ${str}"
    }
    else {
        return "${name} = ${value}"
    }
}

// parse events into attributes
def parse(String description) {
    displayDebugLog("Parsing '${description}'")
    
    def name = null
    def value = null
    def statusTextmsg = ""   
    def msg = parseLanMessage(description)
        
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

    if (description?.startsWith("on/off:")) {
        displayDebugLog("Switch command")
        name = "switch"
        value = description?.endsWith(" 1") ? "on" : "off"
    }
    else if (description?.startsWith("temperature")) {
    	displayDebugLog("Temperature")
        name = "temperature"
        value = device.currentValue("temperature")
    }
    else if (description?.startsWith("humidity")) {
    	displayDebugLog("Humidity")
        name = "humidity"
        value = device.currentValue("humidity")
    }
    else if (description?.startsWith("targetTemperature")) {
    	displayDebugLog("targetTemperature")
        name = "targetTemperature"
        value = device.currentValue("targetTemperature")
    }
    else if (description?.startsWith("fanLevel")) {
    	displayDebugLog("fanLevel")
        name = "fanLevel"
        value = device.currentValue("fanLevel")
    }
    else if (description?.startsWith("currentmode")) {
    	displayDebugLog("mode")
        name = "currentmode"
        value = device.currentValue("currentmode")
    }
    else if (description?.startsWith("on")) {
    	displayDebugLog("on")
        name = "on"
        value = device.currentValue("on")
    }
    else if (description?.startsWith("switch")) {
    	displayDebugLog("switch")
        name = "switch"
        value = device.currentValue("on")
    }
    else if (description?.startsWith("temperatureUnit")) {
    	displayDebugLog("temperatureUnit")
        name = "temperatureUnit"
        value = device.currentValue("temperatureUnit")
    }
    else if (description?.startsWith("Error")) {
    	displayDebugLog("Error")
        name = "Error"
        value = device.currentValue("Error")
    }
    else if (description?.startsWith("voltage")) {
    	displayDebugLog("voltage")
        name = "voltage"
        value = device.currentValue("voltage")
    }
    else if (description?.startsWith("battery")) {
    	displayDebugLog("battery")
        name = "battery"
        value = device.currentValue("battery")
    }
    else if (description?.startsWith("swing")) {
    	displayDebugLog("swing")
        name = "swing"
        value = device.currentValue("swing")
    }
	
    def result = createEvent(name: name, value: value)
    displayDebugLog("Parse returned ${result?.descriptionText}")
    return result
}

def ping(){
	displayTraceLog( "calling parent ping()")
	return parent.ping()
}
