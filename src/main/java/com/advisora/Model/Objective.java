package com.advisora.Model;

public class Objective {
        private int id;
        private String nomObjective;
        private String description;
        private int strategieId;
        private int priority;


        public Objective() {
        }

    public Objective(int strategieId, String nomObjective, String description, int priority) {
        this.strategieId = strategieId;
        this.description = description;
        this.priority = priority;
        this.nomObjective = nomObjective;
    }
        public Objective(int id, int strategieId, String nomObjective, String description, int priority) {
            this.id = id;
            this.strategieId = strategieId;
            this.description = description;
            this.priority = priority;
            this.nomObjective = nomObjective;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
        public int getStrategieId() {
            return strategieId;
        }
        public void setStrategieId(int strategieId) {
            this.strategieId = strategieId;
        }
        public String getNomObjective() {
            return nomObjective;
        }
        public void setNomObjective(String nomObjective) {
            this.nomObjective = nomObjective;
        }
        @Override
        public String toString() {
            return "Objective{id=" + id + ", strategieId=" + strategieId +
                ", description='" + description + "', priority=" + priority + "}";
    }

}
