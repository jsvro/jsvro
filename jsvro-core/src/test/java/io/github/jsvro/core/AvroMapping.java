package io.github.jsvro.core;

import io.github.jsvro.core.Areas.Area;
import io.github.jsvro.core.Areas.AreaType;
import io.github.jsvro.core.Areas.Point;
import io.github.jsvro.core.FxTransactions.Direction;
import io.github.jsvro.core.FxTransactions.FxTransactionFlow;
import io.github.jsvro.core.FxTransactions.FxTransactionResponse;
import io.github.jsvro.core.FxTransactions.FxTransactionState;
import io.github.jsvro.core.FxTransactions.PricingType;
import io.github.jsvro.core.FxTransactions.Side;
import io.github.jsvro.core.People.Address;
import io.github.jsvro.core.People.Person;
import io.github.jsvro.core.avro.AvroAddress;
import io.github.jsvro.core.avro.AvroArea;
import io.github.jsvro.core.avro.AvroAreaType;
import io.github.jsvro.core.avro.AvroDirection;
import io.github.jsvro.core.avro.AvroFxTransaction;
import io.github.jsvro.core.avro.AvroFxTransactionFlow;
import io.github.jsvro.core.avro.AvroFxTransactionState;
import io.github.jsvro.core.avro.AvroPerson;
import io.github.jsvro.core.avro.AvroPoint;
import io.github.jsvro.core.avro.AvroPricingType;
import io.github.jsvro.core.avro.AvroSide;

import java.util.ArrayList;
import java.util.List;

final class AvroMapping {
    private AvroMapping() {
    }

    static AvroPerson toAvro(Person person) {
        return new AvroPerson(person.name(), person.age(), person.born(), toAvro(person.address()), person.roles());
    }

    static Person fromAvro(AvroPerson person) {
        return new Person(person.getName(), person.getAge(), person.getBorn(), fromAvro(person.getAddress()), person.getRoles());
    }

    static AvroAddress toAvro(Address address) {
        return new AvroAddress(address.street(), address.city(), address.country(), toAvro(address.postal()));
    }

    static Address fromAvro(AvroAddress address) {
        return new Address(address.getStreet(), address.getCity(), address.getCountry(), fromAvro(address.getPostal()));
    }

    static AvroArea toAvro(Area area) {
        List<AvroPoint> boundary = new ArrayList<>(area.getBoundary().size());
        for (Point point : area.getBoundary()) {
            boundary.add(new AvroPoint(point.getLat(), point.getLon()));
        }
        return new AvroArea(AvroAreaType.valueOf(area.getAreaType().name()), area.getCode(), area.getShortName(),
                area.getName(), boundary);
    }

    static Area fromAvro(AvroArea avro) {
        List<Point> boundary = new ArrayList<>(avro.getBoundary().size());
        for (AvroPoint point : avro.getBoundary()) {
            boundary.add(new Point(point.getLat(), point.getLon()));
        }
        Area area = new Area();
        area.setAreaType(AreaType.valueOf(avro.getAreaType().name()));
        area.setCode(avro.getCode());
        area.setShortName(avro.getShortName());
        area.setName(avro.getName());
        area.setBoundary(boundary);
        return area;
    }

    static AvroFxTransaction toAvro(FxTransactionResponse fx) {
        return new AvroFxTransaction(
                fx.id(), fx.idempotencyKey(), fx.systemId(), fx.systemReferenceId(), fx.correlationId(),
                toAvro(fx.owner()), fx.bic(), fx.department(), fx.localCurrency(), fx.countryCode(), fx.productId(),
                fx.marketProduct(), fx.buyCustomerNumber(), fx.buyAccountNumber(), fx.buyCurrency(), fx.buyValueDate(),
                fx.sellCustomerNumber(), fx.sellAccountNumber(), fx.sellCurrency(), fx.sellValueDate(),
                AvroSide.valueOf(fx.dealtSide().name()), fx.dealtAmount(), AvroDirection.valueOf(fx.direction().name()),
                fx.transactionTime(), fx.registeringUser(), fx.verifyingUser(), fx.fxRate(), fx.quotedAt(),
                fx.settledBuyAmount(), fx.settledSellAmount(), fx.settlementSystem(),
                AvroPricingType.valueOf(fx.pricingType().name()), AvroFxTransactionFlow.valueOf(fx.flow().name()),
                AvroFxTransactionState.valueOf(fx.status().name()), fx.appliedFxRateOfferId(), fx.reversesFxTransactionId());
    }

    static FxTransactionResponse fromAvro(AvroFxTransaction fx) {
        return new FxTransactionResponse(
                fx.getId(), fx.getIdempotencyKey(), fx.getSystemId(), fx.getSystemReferenceId(), fx.getCorrelationId(),
                fromAvro(fx.getOwner()), fx.getBic(), fx.getDepartment(), fx.getLocalCurrency(), fx.getCountryCode(),
                fx.getProductId(), fx.getMarketProduct(), fx.getBuyCustomerNumber(), fx.getBuyAccountNumber(),
                fx.getBuyCurrency(), fx.getBuyValueDate(), fx.getSellCustomerNumber(), fx.getSellAccountNumber(),
                fx.getSellCurrency(), fx.getSellValueDate(), Side.valueOf(fx.getDealtSide().name()), fx.getDealtAmount(),
                Direction.valueOf(fx.getDirection().name()), fx.getTransactionTime(), fx.getRegisteringUser(),
                fx.getVerifyingUser(), fx.getFxRate(), fx.getQuotedAt(), fx.getSettledBuyAmount(),
                fx.getSettledSellAmount(), fx.getSettlementSystem(), PricingType.valueOf(fx.getPricingType().name()),
                FxTransactionFlow.valueOf(fx.getFlow().name()), FxTransactionState.valueOf(fx.getStatus().name()),
                fx.getAppliedFxRateOfferId(), fx.getReversesFxTransactionId());
    }
}
