import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 10 },   // Ramp-up to 10 users over 1 minute
        { duration: '5m', target: 20 },   // Stay at 10 users for 2 minutes
        { duration: '5m', target: 40 },   // Stay at 20 users for 5 minutes
        { duration: '5m', target: 60 },   // Stay at 30 users for 5 minutes
        { duration: '5m', target: 80 },   // Stay at 30 users for 5 minutes
    ],
};

export default function () {
    http.get('https://petstorepetservice-api.azurewebsites.net/petstorepetservice/v2/pet/1');
    sleep(1);
}
