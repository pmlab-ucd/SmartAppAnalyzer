package fu.hao.analysis;

public class script1503519365690 extends groovy.lang.Script {

    public script1503519365690() {
    }

    public script1503519365690(groovy.lang.Binding context) {
        super(context)
    }

    public static void main(java.lang.String[] args) {
        org.codehaus.groovy.runtime.InvokerHelper.runScript(script1503519365690, args)
    }

    public java.lang.Object run() {
        this.definition(['name': 'Test', 'namespace': 'test', 'author': 'James Fu', 'description': 'For testing', 'category': 'Family', 'iconUrl': 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png', 'iconX2Url': 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png', 'iconX3Url': 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'])
        this.preferences({
                this.section('Turn on when motion detected:', {
                        this.input(['required': true, 'title': 'Where?'], 'themotion', 'capability.motionSensor')
            })
        this.section('Turn off when there\'s been no movement for', {
                this.input(['required': true, 'title': 'Minutes?'], 'minutes', 'number')
            })
        this.section('Turn on this light', {
                this.input(['required': true], 'theswitch', 'capability.switch')
            })
        })
    }

    public java.lang.Object installed() {
        this.initialize()
    }

    public java.lang.Object updated() {
        this.unsubscribe()
        this.initialize()
    }

    public java.lang.Object initialize() {
        this.subscribe(themotion, 'motion.active', motionDetectedHandler)
        this.subscribe(themotion, 'motion.inactive', motionStoppedHandler)
    }

    public java.lang.Object motionDetectedHandler(java.lang.Object evt) {
        log.debug("motionDetectedHandler called: $evt")
        theswitch.on()
    }

    public java.lang.Object motionStoppedHandler(java.lang.Object evt) {
        log.debug("motionStoppedHandler called: $evt")
        this.runIn(60 * minutes , checkMotion)
    }

    public java.lang.Object checkMotion() {
        log.debug('In checkMotion scheduled method')
        java.lang.Object motionState = themotion.currentState('motion')
        if ( motionState .value == 'inactive') {
            java.lang.Object elapsed = this.now() - motionState .date.time
            java.lang.Object threshold = 1000 * 60 * minutes
            if ( elapsed >= threshold ) {
                log.debug("Motion has stayed inactive long enough since last check ($elapsed ms):  turning switch off")
                theswitch.off()
            } else {
                log.debug("Motion has not stayed inactive long enough since last check ($elapsed ms):  doing nothing")
            }
        } else {
            log.debug('Motion is active, do nothing and wait for inactive')
        }
    }

}
