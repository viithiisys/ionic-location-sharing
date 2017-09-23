import { Component, ViewChild, ElementRef } from '@angular/core';
import { NavController, Platform, LoadingController, Loading,Events   } from 'ionic-angular';
import { GoogleMaps,GoogleMap,GoogleMapsEvent,LatLng,CameraPosition,MarkerOptions,Marker} from '@ionic-native/google-maps';
import { Geolocation } from '@ionic-native/geolocation';
import { LocationdetailProvider } from '../../providers/locationdetail/locationdetail';

declare var google; 

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {
  mapElement: HTMLElement;
  map: GoogleMap;
  nearByList:any;
  location:any;
  latitude:any;
  longitude:any;
  loading: Loading;
  constructor(private googleMaps: GoogleMaps, private geolocation: Geolocation,public events: Events, public loadingCtrl: LoadingController,public navCtrl: NavController,private platform: Platform,public locationProvider: LocationdetailProvider) {
    this.nearByList = [];
    this.loadingStart();  

    platform.ready().then(() => {
      geolocation.getCurrentPosition().then(pos => {
        this.latitude = pos.coords.latitude;
        this.longitude = pos.coords.longitude;
        console.log('lat: ' + pos.coords.latitude + ', lon: ' + pos.coords.longitude);
        this.getNearbylocation();
      });
    });
  } 


            

            

      getNearbylocation(){
        this.locationProvider.getNearbyplaces(this.latitude+","+this.longitude).subscribe(
           locationList=>{
             this.nearByList = locationList.results;
             this.loadMap()
               this.loading.dismissAll();
           },err =>{
               this.loading.dismissAll();
           },
            () =>{
        })
    }
 
    loadMap(){
       this.location = new LatLng(this.latitude,this.longitude);
        // this.setMarker();
       console.log("location===>",this.location)
        this.map = new GoogleMap('map', {
          // 'backgroundColor': 'white',
          'controls': {
            'compass': true,
            'myLocationButton': true,
            'indoorPicker': true
            // 'zoom': false
          },
          'gestures': {
            'scroll': true,
            'tilt': true,
            'rotate': true,
            'zoom': true
          },
          'camera': {
             'target': {
                'lat': this.latitude,
                'lng': this.longitude
              },
            'tilt': 30,
            'zoom': 9,
            'bearing': 50
          }
        });
 
        this.map.one(GoogleMapsEvent.MAP_READY).then(() => {
           this.loadingStop();
           this.setMarker();
            console.log('Map is ready!');
        });
    
 
    }

    setMarker(){
      for (var i = 0; i < this.nearByList.length; i++){
        let markerOptions: MarkerOptions = {
          position: new LatLng(this.nearByList[i].geometry.location.lat,this.nearByList[i].geometry.location.lng)
        };
            
        this.map.addMarker(markerOptions)
          .then((marker: Marker) => {
            console.log("marker",marker)
            marker.showInfoWindow();
        });
      }
    }

    loadingStart(){
       this.loading = this.loadingCtrl.create({
        content: 'Please Wait...',
      });
      this.loading.present(); 
    }

    loadingStop(){
         this.loading.dismissAll();
    }
}
