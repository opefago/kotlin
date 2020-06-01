/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.signaturer

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.backend.Fir2IrSignatureComposer
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class FirBasedSignatureComposer : Fir2IrSignatureComposer {
    class SignatureBuilder : FirVisitorVoid() {
        var hashId: Long? = null
        var mask = 0L

        private fun setExpected(f: Boolean) {
            mask = mask or IdSignature.Flags.IS_EXPECT.encode(f)
        }

        override fun visitElement(element: FirElement) {
            TODO("Should not be here")
        }

        override fun visitRegularClass(regularClass: FirRegularClass) {
            setExpected(regularClass.isExpect)
            //platformSpecificClass(descriptor)
        }

        override fun visitConstructor(constructor: FirConstructor) {
            setExpected(constructor.isExpect)
        }

        override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
            setExpected(simpleFunction.isExpect)
        }

        override fun visitProperty(property: FirProperty) {
            setExpected(property.isExpect)
        }
    }

    private val CallableId.relativeCallableName: FqName
        get() = className?.child(callableName) ?: FqName.topLevel(callableName)

    override fun composeSignature(declaration: FirDeclaration): IdSignature {
        val builder = SignatureBuilder()
        declaration.accept(builder)
        return when {
            declaration is FirRegularClass && declaration.visibility != Visibilities.LOCAL -> {
                val classId = declaration.classId
                IdSignature.PublicSignature(classId.packageFqName, classId.relativeClassName, builder.hashId, builder.mask)
            }
            declaration is FirCallableMemberDeclaration<*> -> {
                val callableId = declaration.symbol.callableId
                IdSignature.PublicSignature(callableId.packageName, callableId.relativeCallableName, builder.hashId, builder.mask)
            }
            else -> throw AssertionError("Unsupported FIR declaration in signature composer: ${declaration.render()}")
        }
    }

    override fun composeAccessorSignature(property: FirProperty, isSetter: Boolean): IdSignature {
        val propertySignature = composeSignature(property) as IdSignature.PublicSignature
        val accessorFqName = if (isSetter) {
            propertySignature.declarationFqn.child(Name.special("<set-${property.name}>"))
        } else {
            propertySignature.declarationFqn.child(Name.special("<get-${property.name}>"))
        }
        return IdSignature.PublicSignature(propertySignature.packageFqn, accessorFqName, propertySignature.id, propertySignature.mask)
    }
}