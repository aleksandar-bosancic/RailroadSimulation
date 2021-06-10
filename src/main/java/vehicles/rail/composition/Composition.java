package vehicles.rail.composition;

import map.*;
import vehicles.rail.locomotive.Locomotive;
import vehicles.rail.wagon.Wagon;

import java.util.List;
import java.util.Optional;

public class Composition extends Thread {
    private final String label;
    private final Locomotive frontLocomotive;
    private final Locomotive rearLocomotive;
    private final List<Wagon> wagonList;
    private final int length;
    private final Station destinationStation;
    private final Station departureStation;
    private final List<Station> stationList;
    private Station currentStation;
    private Field currentField;
    private Field previousField;
    private Field nextField;
    private Field lastCompositionItemField;
    private boolean departed;
    private boolean finished;
    private boolean updated;
    private boolean stationExit;
    private final int movementSpeed;
    private int temporaryMovementSpeed;
    private boolean readyToGo;
    private boolean everythingInStation;
    private boolean waiting;
    private boolean passedCrossing;
    private int waitingTime;

    public Composition(String label, Locomotive frontLocomotive, Locomotive rearLocomotive, List<Wagon> wagonList,
                       List<Station> stationList, int movementSpeed) {
        this.label = label;
        this.frontLocomotive = frontLocomotive;
        this.rearLocomotive = rearLocomotive;
        this.wagonList = wagonList;
        this.destinationStation = stationList.get(stationList.size() - 1);
        this.currentField = stationList.get(0).getStationFields().get(0);
        this.departureStation = stationList.get(0);
        currentStation = departureStation;
        this.movementSpeed = movementSpeed;
        this.stationList = stationList;
        this.stationList.remove(0);
        this.stationList.remove(stationList.size() - 1);
        departed = false;
        waitingTime = 0;
        var tempLength = 1;
        if(rearLocomotive != null){
            tempLength++;
        }
        if(wagonList != null){
            tempLength = wagonList.size();
        }
        length = tempLength;
    }

    private void setFieldsForItems(){
        Field tempPreviousField = null;
        frontLocomotive.setCurrentField(currentField);
        if(previousField != null){
            frontLocomotive.setPreviousField(previousField);
            tempPreviousField = previousField;
            if(wagonList != null) {
                for (Wagon wagon : wagonList) {
                    wagon.setPreviousField(wagon.getCurrentField());
                    wagon.setCurrentField(tempPreviousField);
                    tempPreviousField = wagon.getPreviousField();
                }
            }
        }
        if(rearLocomotive != null) {
            if(wagonList == null) {
                tempPreviousField = rearLocomotive.getCurrentField();
                rearLocomotive.setCurrentField(previousField);
                rearLocomotive.setPreviousField(tempPreviousField);
            } else {
                tempPreviousField = rearLocomotive.getCurrentField();
                rearLocomotive.setCurrentField(wagonList.get(wagonList.size() - 1).getPreviousField());
                rearLocomotive.setPreviousField(tempPreviousField);
            }
            tempPreviousField = rearLocomotive.getPreviousField();
        }
        lastCompositionItemField = tempPreviousField;
        if(lastCompositionItemField != null){
            checkIfCrossingPassed();
        }
    }

    public void move(){
        var tempField = currentField;
        nextField = Map.getNextFieldTrain(currentField, previousField);
        currentField = nextField;
        previousField = tempField;
        setFieldsForItems();
    }

    int crossingFieldsPassed = 0;
    private void checkIfCrossingPassed() {
        var crossing = Map.getRailroadCrossingList().stream()
                .filter(railroadCrossing -> railroadCrossing.checkCrossingFields(lastCompositionItemField)).findFirst();
        if(crossing.isPresent() && crossing.get().checkCrossingFields(lastCompositionItemField)){
            crossingFieldsPassed++;
            if(crossingFieldsPassed == 2) {
                passedCrossing = true;
                currentStation.notifyCrossing(this);
                crossingFieldsPassed = 0;
            }
        }
    }

    public boolean checkStationField(){
        return currentField.getFieldType().equals(FieldType.STATION);
    }

    private boolean everythingInStation(){
        var frontIn = false;
        var rearIn = false;
        var wagonsIn = false;
        if(frontLocomotive.getCurrentField().getFieldType().equals(FieldType.STATION)){
            frontIn = true;
        }
        if(rearLocomotive == null || rearLocomotive.getCurrentField().getFieldType().equals(FieldType.STATION)){
            rearIn = true;
        }
        if(wagonList == null || wagonList.stream().allMatch(wagon -> wagon.getCurrentField().getFieldType().equals(FieldType.STATION))){
            wagonsIn = true;
        }
        return frontIn && rearIn && wagonsIn;
    }

    public void moveTrainIntoStation(){
        if(!currentField.equals(currentStation.getStationFields().get(1))) {
            setCurrentField(currentStation.getStationFields().get(1));
            setPreviousField(currentStation.trainDirection(departureStation));
        } else {
            setPreviousField(currentStation.getStationFields().get(1));
        }
        setFieldsForItems();
    }

    private boolean checkIsStationExit(){
        Optional<Station> optionalStation = stationList.stream().filter(station ->
                                        station.getStationExitFields().stream().anyMatch(field -> currentField.equals(field))).findFirst();
        if(optionalStation.isPresent()){
            currentStation = optionalStation.get();
            return true;
        } else if (destinationStation.getStationExitFields().stream().anyMatch(field -> currentField.equals(field))){
            currentStation = destinationStation;
            return true;
        }
        return false;
    }

    private boolean checkIfDepartureStationField(){
        return departureStation.getStationFields().stream().anyMatch(field -> currentField.equals(field));
    }

    @Override
    public void run() {
        while (!finished) {
            try {
            if(!departed && checkIfDepartureStationField()){                                                            //Ako je na pocetnoj stanici i ako je trenutno polje tipa STATION
                if(departureStation.safeToGo(this)) {
                    if(waitingTime == 0) {
                        setCurrentField(departureStation.trainDirection(destinationStation));                           //Postavi izlazno polje koje ide prema destination kao trenutno
                        waiting = false;
                        departed = true;
                    } else {
                        sleep(waitingTime);
                        setCurrentField(departureStation.trainDirection(destinationStation));
                        waiting = false;
                        departed = true;
                        waitingTime = 0;
                    }
                } else {
                    waiting = true;
                }
            }
            if(checkIsStationExit()){                                                                                   //Provjera da li se voz nalazi na ulaznom/izlaznom polju neke stanice
                stationExit = true;                                                                                     //Koja nije destination ili departure
            }
            if(stationExit){                                                                                            //Ako se nalazi na ulazu u stanicu
                moveTrainIntoStation();                                                                                 //Ubaci element voza koji nije u stanicu, u stanicu
                if(everythingInStation()){                                                                              //Provjeri da li je sve u stanici
                    if(currentStation.equals(destinationStation)){                                                      //Ako je trenutna stanica krajnja, zavrsi sve
                        destinationStation.removeCompositionFromSegment(this);
                        updated = true;
                        finished = true;
                    }
                    everythingInStation = true;
                    stationExit = false;
                }
            }
            if(!stationExit && checkStationField()){
                if(everythingInStation && readyToGo) {
                    currentStation.removeCompositionFromSegment(this);
                    if(currentStation.equals(destinationStation)){
                        break;
                    }
                    if(currentStation.safeToGo(this)) {
                        if(waitingTime == 0) {
                            setCurrentField(currentStation.trainDirection(destinationStation));
                            stationList.remove(currentStation);
                            readyToGo = false;
                            everythingInStation = false;
                            waiting = false;
                            passedCrossing = false;
                        } else {
                            passedCrossing = false;
                            sleep(waitingTime);
                            setCurrentField(currentStation.trainDirection(destinationStation));
                            stationList.remove(currentStation);
                            readyToGo = false;
                            everythingInStation = false;
                            waiting = false;
                            waitingTime = 0;
                        }
                    } else {
                        waiting = true;
                    }
                }
            }
            if(!stationExit && !everythingInStation && departed) {
                move();
            }
            if(!waiting) {
                updated = true;
            }
                if(waiting){
                    sleep(200);
                }
                if(!stationExit && everythingInStation && !waiting){
                    sleep(3000);
                    readyToGo = true;
                } else {
                    if(temporaryMovementSpeed != 0){
                        sleep(10_000 / temporaryMovementSpeed);
                    } else {
                        sleep(10_000 / movementSpeed);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            updated = false;
        }
    }

    public Locomotive getFrontLocomotive() {
        return frontLocomotive;
    }

    public Locomotive getRearLocomotive() {
        return rearLocomotive;
    }

    public List<Wagon> getWagonList() {
        return wagonList;
    }

    public Station getDestinationStation() {
        return destinationStation;
    }

    public Field getCurrentField() {
        return currentField;
    }

    public void setCurrentField(Field currentField) {
        this.currentField = currentField;
    }

    public Field getPreviousField() {
        return previousField;
    }

    public void setPreviousField(Field previousField) {
        this.previousField = previousField;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    public Station getDepartureStation() {
        return departureStation;
    }

    public int getMovementSpeed() {
        if(temporaryMovementSpeed != 0){
            return temporaryMovementSpeed;
        }
        return movementSpeed;
    }

    public void setTemporaryMovementSpeed(int movementSpeed){
        this.temporaryMovementSpeed = movementSpeed;
    }

    public String getLabel() {
        return label;
    }

    public int getLength() {
        return length;
    }

    public void setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
    }

    public boolean isPassedCrossing() {
        return passedCrossing;
    }

    @Override
    public String toString() {
        return "Composition{" +
                "label='" + label + '\'' +
                '}';
    }
}
