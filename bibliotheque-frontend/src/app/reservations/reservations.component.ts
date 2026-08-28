import { Component, OnInit } from '@angular/core';
import { Books } from '../_model/books';
import { Reservation, ReservationAffichage, ReservationRequest, ReservationStatus } from '../_model/reservation';
import { Users } from '../_model/users';
import { ReservationService } from '../_service/reservation.service';

@Component({
  selector: 'app-reservations',
  templateUrl: './reservations.component.html',
  styleUrls: ['./reservations.component.css']
})
export class ReservationsComponent implements OnInit {

  reservations: Reservation[] = [];
  livres: Books[] = [];
  adherents: Users[] = [];
  statutFiltre?: ReservationStatus;

  constructor(private reservationService: ReservationService) { }

  ngOnInit(): void {
    this.chargerListesDeReference();
    this.chargerReservations();
  }

  get reservationsAffichables(): ReservationAffichage[] {
    return this.reservations.map(reservation => ({
      ...reservation,
      titreLivre: this.libelleLivre(reservation.livreId),
      nomAdherent: this.libelleAdherent(reservation.adherentId),
    }));
  }

  private chargerListesDeReference() {
    this.reservationService.listerLivres().subscribe(data => this.livres = data);
    this.reservationService.listerAdherents().subscribe(data => this.adherents = data);
  }

  chargerReservations() {
    this.reservationService.listerReservations(this.statutFiltre).subscribe(data => {
      this.reservations = data;
    });
  }

  onFiltreChange(statut?: ReservationStatus) {
    this.statutFiltre = statut;
    this.chargerReservations();
  }

  onCreerReservation(request: ReservationRequest) {
    this.reservationService.creerReservation(request).subscribe(() => {
      this.chargerReservations();
    });
  }

  onAnnulerReservation(id: number) {
    this.reservationService.annulerReservation(id).subscribe(() => {
      this.chargerReservations();
    });
  }

  private libelleLivre(livreId: number): string {
    const livre = this.livres.find(l => l.bookId === livreId);
    return livre ? livre.bookName : `Livre #${livreId}`;
  }

  private libelleAdherent(adherentId: number): string {
    const adherent = this.adherents.find(a => a.userId === adherentId);
    return adherent ? adherent.name : `Adhérent #${adherentId}`;
  }
}
