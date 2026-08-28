import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ReservationAffichage, ReservationStatus } from '../_model/reservation';

@Component({
  selector: 'app-reservations-list',
  templateUrl: './reservations-list.component.html',
  styleUrls: ['./reservations-list.component.css']
})
export class ReservationsListComponent {

  @Input() reservations: ReservationAffichage[] = [];
  @Input() statutFiltre?: ReservationStatus;

  @Output() filtreChange = new EventEmitter<ReservationStatus | undefined>();
  @Output() annuler = new EventEmitter<number>();

  statutsDisponibles = Object.values(ReservationStatus);

  estAnnulable(statut: ReservationStatus): boolean {
    return statut === ReservationStatus.EN_ATTENTE || statut === ReservationStatus.DISPONIBLE;
  }

  onFiltreChange(statut?: ReservationStatus) {
    this.filtreChange.emit(statut);
  }

  onAnnuler(id: number) {
    this.annuler.emit(id);
  }
}
