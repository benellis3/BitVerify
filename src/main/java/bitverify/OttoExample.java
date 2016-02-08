package bitverify;

/**
 * Created by Rob on 08/02/2016.
 */

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class OttoExample {

    public class OttoEvent {
        private String message;

        public OttoEvent(String m) {
            message = m;
        }

        public String getMessage() {
            return message;
        }
    }


    public class OttoExamplePublisher {
        private Bus eventBus;

        public OttoExamplePublisher(Bus eventBus) {
            this.eventBus = eventBus;
        }

        public void raiseTheEvent() {
            eventBus.post(new OttoEvent("Hello!"));
        }
    }

    public class OttoExampleSubscriber {
        private Bus eventBus;

        public OttoExampleSubscriber(Bus eventBus) {
            this.eventBus = eventBus;
            // very important: hook up our even handlers to the bus.
            eventBus.register(this);
            // later on we might call unregister to allow our object to be garbage collected
            // this is necessary for short-lived objects otherwise the bus keeps them alive.
            // eventBus.unregister(this);
        }

        // subscribe to the type of event indicated by the argument
        @Subscribe
        public void onOttoEvent(OttoEvent e) {
            System.out.println(e.getMessage());
        }

        // subscriptions use inheritance: here we subscribe to any event
        // (they all inherit from object)
        @Subscribe
        public void onAnyEvent(Object o) {
            // do stuff
        }
    }

}